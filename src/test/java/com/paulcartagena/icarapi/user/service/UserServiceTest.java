package com.paulcartagena.icarapi.user.service;

import com.paulcartagena.icarapi.auth.repository.RefreshTokenRepository;
import com.paulcartagena.icarapi.exception.ApiException;
import com.paulcartagena.icarapi.user.dto.ChangePasswordRequest;
import com.paulcartagena.icarapi.user.dto.CreateUserRequest;
import com.paulcartagena.icarapi.user.dto.ResetPasswordRequest;
import com.paulcartagena.icarapi.user.dto.UpdateUserRequest;
import com.paulcartagena.icarapi.user.dto.UserResponse;
import com.paulcartagena.icarapi.user.entity.AppUser;
import com.paulcartagena.icarapi.user.enums.UserRole;
import com.paulcartagena.icarapi.user.enums.UserStatus;
import com.paulcartagena.icarapi.user.repository.AppUserRepository;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final String PASSWORD = "a-strong-password";

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    private AppUser stored(UserRole role) {
        AppUser user = new AppUser();
        user.setId(UUID.randomUUID());
        user.setUsername(role.name().toLowerCase());
        user.setFullName("Some " + role);
        user.setPasswordHash("hashed");
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        when(appUserRepository.findById(user.getId())).thenReturn(Optional.of(user));
        return user;
    }

    private static void assertApiException(ThrowingCallable call, HttpStatus status) {
        assertThatThrownBy(call)
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(status);
    }

    @Nested
    class CreateUser {

        private CreateUserRequest request(UserRole role) {
            return new CreateUserRequest("  newuser  ", "  New User  ", PASSWORD, role);
        }

        // actor, role to create, allowed?
        @ParameterizedTest(name = "{0} creating a {1} -> allowed: {2}")
        @CsvSource({
                "ADMIN, ADMIN, true",
                "ADMIN, PASTOR, true",
                "ADMIN, TREASURER, true",
                "PASTOR, ADMIN, false",
                "PASTOR, PASTOR, false",
                "PASTOR, TREASURER, true",
                "TREASURER, TREASURER, false"
        })
        void onlyAssignsRolesBelowTheActorUnlessAdmin(UserRole actorRole, UserRole newRole, boolean allowed) {
            AppUser actor = stored(actorRole);

            if (allowed) {
                when(passwordEncoder.encode(PASSWORD)).thenReturn("new-hash");
                when(appUserRepository.save(any(AppUser.class))).thenAnswer(invocation -> invocation.getArgument(0));

                UserResponse response = userService.createUser(request(newRole), actor.getId());

                assertThat(response.role()).isEqualTo(newRole);
                assertThat(response.status()).isEqualTo(UserStatus.ACTIVE);
            } else {
                assertApiException(() -> userService.createUser(request(newRole), actor.getId()), HttpStatus.FORBIDDEN);
                verify(appUserRepository, never()).save(any());
            }
        }

        @Test
        void trimsTextAndHashesThePassword() {
            AppUser actor = stored(UserRole.ADMIN);
            when(passwordEncoder.encode(PASSWORD)).thenReturn("new-hash");
            when(appUserRepository.save(any(AppUser.class))).thenAnswer(invocation -> invocation.getArgument(0));

            userService.createUser(request(UserRole.TREASURER), actor.getId());

            verify(appUserRepository).save(argThat(user ->
                    user.getUsername().equals("newuser")
                            && user.getFullName().equals("New User")
                            && user.getPasswordHash().equals("new-hash")));
        }

        @Test
        void rejectsDuplicateUsernameIgnoringCase() {
            AppUser actor = stored(UserRole.ADMIN);
            when(appUserRepository.existsByUsernameIgnoreCase("newuser")).thenReturn(true);

            assertApiException(() -> userService.createUser(request(UserRole.TREASURER), actor.getId()), HttpStatus.CONFLICT);
            verify(appUserRepository, never()).save(any());
        }
    }

    @Nested
    class UpdateUser {

        @Test
        void pastorCanEditATreasurer() {
            AppUser pastor = stored(UserRole.PASTOR);
            AppUser treasurer = stored(UserRole.TREASURER);

            UserResponse response = userService.updateUser(treasurer.getId(),
                    new UpdateUserRequest("  Renamed  ", UserRole.TREASURER), pastor.getId());

            assertThat(response.fullName()).isEqualTo("Renamed");
        }

        @Test
        void pastorCannotPromoteATreasurerToPastor() {
            AppUser pastor = stored(UserRole.PASTOR);
            AppUser treasurer = stored(UserRole.TREASURER);

            assertApiException(() -> userService.updateUser(treasurer.getId(),
                    new UpdateUserRequest("Name", UserRole.PASTOR), pastor.getId()), HttpStatus.FORBIDDEN);
            assertThat(treasurer.getRole()).isEqualTo(UserRole.TREASURER);
        }

        @Test
        void pastorCannotEditAnotherPastor() {
            AppUser pastor = stored(UserRole.PASTOR);
            AppUser otherPastor = stored(UserRole.PASTOR);

            assertApiException(() -> userService.updateUser(otherPastor.getId(),
                    new UpdateUserRequest("Name", UserRole.PASTOR), pastor.getId()), HttpStatus.FORBIDDEN);
        }

        @Test
        void adminCanDemoteAnotherAdmin() {
            AppUser admin = stored(UserRole.ADMIN);
            AppUser otherAdmin = stored(UserRole.ADMIN);

            UserResponse response = userService.updateUser(otherAdmin.getId(),
                    new UpdateUserRequest("Name", UserRole.PASTOR), admin.getId());

            assertThat(response.role()).isEqualTo(UserRole.PASTOR);
        }

        @Test
        void nobodyEditsThemselvesHere() {
            AppUser admin = stored(UserRole.ADMIN);

            assertApiException(() -> userService.updateUser(admin.getId(),
                    new UpdateUserRequest("Name", UserRole.TREASURER), admin.getId()), HttpStatus.BAD_REQUEST);
            assertThat(admin.getRole()).isEqualTo(UserRole.ADMIN);
        }
    }

    @Nested
    class ActivateAndDeactivate {

        @Test
        void deactivatingRevokesAllSessions() {
            AppUser pastor = stored(UserRole.PASTOR);
            AppUser treasurer = stored(UserRole.TREASURER);

            UserResponse response = userService.deactivateUser(treasurer.getId(), pastor.getId());

            assertThat(response.status()).isEqualTo(UserStatus.INACTIVE);
            verify(refreshTokenRepository).revokeAllActiveByUserId(eq(treasurer.getId()), any());
        }

        @Test
        void cannotDeactivateYourself() {
            AppUser admin = stored(UserRole.ADMIN);

            assertApiException(() -> userService.deactivateUser(admin.getId(), admin.getId()), HttpStatus.BAD_REQUEST);
            assertThat(admin.getStatus()).isEqualTo(UserStatus.ACTIVE);
        }

        @Test
        void pastorCannotDeactivateAnAdmin() {
            AppUser pastor = stored(UserRole.PASTOR);
            AppUser admin = stored(UserRole.ADMIN);

            assertApiException(() -> userService.deactivateUser(admin.getId(), pastor.getId()), HttpStatus.FORBIDDEN);
            assertThat(admin.getStatus()).isEqualTo(UserStatus.ACTIVE);
            verify(refreshTokenRepository, never()).revokeAllActiveByUserId(any(), any());
        }

        @Test
        void activatingSetsStatusActive() {
            AppUser admin = stored(UserRole.ADMIN);
            AppUser treasurer = stored(UserRole.TREASURER);
            treasurer.setStatus(UserStatus.INACTIVE);

            assertThat(userService.activateUser(treasurer.getId(), admin.getId()).status()).isEqualTo(UserStatus.ACTIVE);
        }

        @Test
        void unknownUserReturns404() {
            AppUser admin = stored(UserRole.ADMIN);
            UUID unknownId = UUID.randomUUID();
            when(appUserRepository.findById(unknownId)).thenReturn(Optional.empty());

            assertApiException(() -> userService.deactivateUser(unknownId, admin.getId()), HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    class Passwords {

        @Test
        void resetSetsNewHashAndRevokesSessions() {
            AppUser pastor = stored(UserRole.PASTOR);
            AppUser treasurer = stored(UserRole.TREASURER);
            when(passwordEncoder.encode(PASSWORD)).thenReturn("new-hash");

            userService.resetPassword(treasurer.getId(), new ResetPasswordRequest(PASSWORD), pastor.getId());

            assertThat(treasurer.getPasswordHash()).isEqualTo("new-hash");
            verify(refreshTokenRepository).revokeAllActiveByUserId(eq(treasurer.getId()), any());
        }

        @Test
        void treasurerCannotResetAnotherTreasurer() {
            AppUser treasurer = stored(UserRole.TREASURER);
            AppUser otherTreasurer = stored(UserRole.TREASURER);

            assertApiException(() -> userService.resetPassword(otherTreasurer.getId(),
                    new ResetPasswordRequest(PASSWORD), treasurer.getId()), HttpStatus.FORBIDDEN);
            assertThat(otherTreasurer.getPasswordHash()).isEqualTo("hashed");
        }

        @Test
        void cannotResetYourOwnPassword() {
            AppUser admin = stored(UserRole.ADMIN);

            assertApiException(() -> userService.resetPassword(admin.getId(),
                    new ResetPasswordRequest(PASSWORD), admin.getId()), HttpStatus.BAD_REQUEST);
        }

        @Test
        void changeOwnPasswordRequiresTheCurrentOne() {
            AppUser treasurer = stored(UserRole.TREASURER);
            when(passwordEncoder.matches("wrong-password", "hashed")).thenReturn(false);

            assertApiException(() -> userService.changeOwnPassword(
                    new ChangePasswordRequest("wrong-password", PASSWORD), treasurer.getId()), HttpStatus.BAD_REQUEST);
            assertThat(treasurer.getPasswordHash()).isEqualTo("hashed");
            verify(refreshTokenRepository, never()).revokeAllActiveByUserId(any(), any());
        }

        @Test
        void changeOwnPasswordUpdatesHashAndRevokesSessions() {
            AppUser treasurer = stored(UserRole.TREASURER);
            when(passwordEncoder.matches("current-password", "hashed")).thenReturn(true);
            when(passwordEncoder.encode(PASSWORD)).thenReturn("new-hash");

            userService.changeOwnPassword(new ChangePasswordRequest("current-password", PASSWORD), treasurer.getId());

            assertThat(treasurer.getPasswordHash()).isEqualTo("new-hash");
            verify(refreshTokenRepository).revokeAllActiveByUserId(eq(treasurer.getId()), any());
        }
    }
}
