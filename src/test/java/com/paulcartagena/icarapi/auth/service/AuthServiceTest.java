package com.paulcartagena.icarapi.auth.service;

import com.paulcartagena.icarapi.auth.dto.LoginRequest;
import com.paulcartagena.icarapi.auth.dto.MeResponse;
import com.paulcartagena.icarapi.auth.entity.RefreshToken;
import com.paulcartagena.icarapi.auth.repository.RefreshTokenRepository;
import com.paulcartagena.icarapi.config.AuthProperties;
import com.paulcartagena.icarapi.exception.ApiException;
import com.paulcartagena.icarapi.user.entity.AppUser;
import com.paulcartagena.icarapi.user.enums.Permission;
import com.paulcartagena.icarapi.user.enums.UserRole;
import com.paulcartagena.icarapi.user.enums.UserStatus;
import com.paulcartagena.icarapi.user.repository.AppUserRepository;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String PASSWORD = "a-strong-password";

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AccessTokenService accessTokenService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        AuthProperties properties = new AuthProperties(Duration.ofMinutes(15), Duration.ofDays(7), true);
        authService = new AuthService(appUserRepository, refreshTokenRepository, passwordEncoder,
                accessTokenService, properties);
    }

    private static AppUser user(UserStatus status) {
        AppUser user = new AppUser();
        user.setId(UUID.randomUUID());
        user.setUsername("paul");
        user.setPasswordHash("hashed");
        user.setFullName("Paul Cartagena");
        user.setRole(UserRole.TREASURER);
        user.setStatus(status);
        return user;
    }

    private static RefreshToken storedToken(AppUser user, LocalDateTime expiresAt, LocalDateTime revokedAt) {
        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setTokenHash("whatever");
        token.setCreatedAt(LocalDateTime.now().minusDays(1));
        token.setExpiresAt(expiresAt);
        token.setRevokedAt(revokedAt);
        return token;
    }

    private static void assertUnauthorized(ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Nested
    class Login {

        @Test
        void returnsTokensAndStoresOnlyTheRefreshTokenHash() {
            AppUser user = user(UserStatus.ACTIVE);
            when(appUserRepository.findByUsername("paul")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(PASSWORD, "hashed")).thenReturn(true);
            when(accessTokenService.createAccessToken(user)).thenReturn("access-token");
            when(accessTokenService.getExpiresInSeconds()).thenReturn(900L);

            AuthTokens tokens = authService.login(new LoginRequest(" paul ", PASSWORD));

            assertThat(tokens.accessToken()).isEqualTo("access-token");
            assertThat(tokens.accessTokenExpiresIn()).isEqualTo(900L);
            assertThat(tokens.refreshToken()).isNotBlank();

            ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
            verify(refreshTokenRepository).save(captor.capture());
            RefreshToken saved = captor.getValue();
            assertThat(saved.getUser()).isSameAs(user);
            assertThat(saved.getTokenHash()).hasSize(64).isNotEqualTo(tokens.refreshToken());
            assertThat(saved.getExpiresAt()).isAfter(LocalDateTime.now().plusDays(6));
            assertThat(saved.getRevokedAt()).isNull();
        }

        @Test
        void rejectsWrongPassword() {
            when(appUserRepository.findByUsername("paul")).thenReturn(Optional.of(user(UserStatus.ACTIVE)));
            when(passwordEncoder.matches(PASSWORD, "hashed")).thenReturn(false);

            assertUnauthorized(() -> authService.login(new LoginRequest("paul", PASSWORD)));
            verify(refreshTokenRepository, never()).save(any());
        }

        @Test
        void rejectsUnknownUserButStillChecksAPassword() {
            when(appUserRepository.findByUsername("ghost")).thenReturn(Optional.empty());

            assertUnauthorized(() -> authService.login(new LoginRequest("ghost", PASSWORD)));
            // BCrypt still runs against the dummy hash, so timing doesn't reveal the user doesn't exist
            verify(passwordEncoder).matches(eq(PASSWORD), any());
            verify(refreshTokenRepository, never()).save(any());
        }

        @Test
        void rejectsInactiveUserEvenWithCorrectPassword() {
            when(appUserRepository.findByUsername("paul")).thenReturn(Optional.of(user(UserStatus.INACTIVE)));
            when(passwordEncoder.matches(PASSWORD, "hashed")).thenReturn(true);

            assertUnauthorized(() -> authService.login(new LoginRequest("paul", PASSWORD)));
            verify(refreshTokenRepository, never()).save(any());
        }
    }

    @Nested
    class Refresh {

        @Test
        void rotatesTheTokenAndIssuesANewOne() {
            AppUser user = user(UserStatus.ACTIVE);
            RefreshToken stored = storedToken(user, LocalDateTime.now().plusDays(1), null);
            when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(stored));
            when(accessTokenService.createAccessToken(user)).thenReturn("new-access-token");

            AuthTokens tokens = authService.refresh("old-refresh-token");

            assertThat(stored.getRevokedAt()).isNotNull();
            assertThat(tokens.accessToken()).isEqualTo("new-access-token");
            assertThat(tokens.refreshToken()).isNotEqualTo("old-refresh-token");
            verify(refreshTokenRepository).save(any(RefreshToken.class));
        }

        @Test
        void reusedTokenRevokesAllSessionsOfTheUser() {
            AppUser user = user(UserStatus.ACTIVE);
            RefreshToken stored = storedToken(user, LocalDateTime.now().plusDays(1), LocalDateTime.now().minusHours(1));
            when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(stored));

            assertUnauthorized(() -> authService.refresh("stolen-token"));
            verify(refreshTokenRepository).revokeAllActiveByUserId(eq(user.getId()), any());
            verify(refreshTokenRepository, never()).save(any());
        }

        @Test
        void rejectsExpiredToken() {
            RefreshToken stored = storedToken(user(UserStatus.ACTIVE), LocalDateTime.now().minusMinutes(1), null);
            when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(stored));

            assertUnauthorized(() -> authService.refresh("expired-token"));
            verify(refreshTokenRepository, never()).save(any());
        }

        @Test
        void rejectsInactiveUserAndRevokesTheirSessions() {
            AppUser user = user(UserStatus.INACTIVE);
            RefreshToken stored = storedToken(user, LocalDateTime.now().plusDays(1), null);
            when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(stored));

            assertUnauthorized(() -> authService.refresh("token"));
            verify(refreshTokenRepository).revokeAllActiveByUserId(eq(user.getId()), any());
            verify(refreshTokenRepository, never()).save(any());
        }

        @Test
        void rejectsUnknownToken() {
            when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

            assertUnauthorized(() -> authService.refresh("unknown"));
        }

        @Test
        void rejectsMissingToken() {
            assertUnauthorized(() -> authService.refresh(null));
            verify(refreshTokenRepository, never()).findByTokenHash(any());
        }
    }

    @Nested
    class Logout {

        @Test
        void revokesTheToken() {
            RefreshToken stored = storedToken(user(UserStatus.ACTIVE), LocalDateTime.now().plusDays(1), null);
            when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(stored));

            authService.logout("token");

            assertThat(stored.getRevokedAt()).isNotNull();
        }

        @Test
        void keepsTheOriginalRevocationDateIfAlreadyRevoked() {
            LocalDateTime revokedAt = LocalDateTime.now().minusHours(1);
            RefreshToken stored = storedToken(user(UserStatus.ACTIVE), LocalDateTime.now().plusDays(1), revokedAt);
            when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(stored));

            authService.logout("token");

            assertThat(stored.getRevokedAt()).isEqualTo(revokedAt);
        }

        @Test
        void doesNothingWithoutAToken() {
            authService.logout(null);

            verify(refreshTokenRepository, never()).findByTokenHash(any());
        }
    }

    @Test
    void getCurrentUserReturnsRoleAndPermissions() {
        AppUser user = user(UserStatus.ACTIVE);
        when(appUserRepository.findById(user.getId())).thenReturn(Optional.of(user));

        MeResponse me = authService.getCurrentUser(user.getId());

        assertThat(me.username()).isEqualTo("paul");
        assertThat(me.role()).isEqualTo(UserRole.TREASURER);
        assertThat(me.permissions()).containsExactly(Permission.FINANCE_READ, Permission.FINANCE_WRITE);
    }
}
