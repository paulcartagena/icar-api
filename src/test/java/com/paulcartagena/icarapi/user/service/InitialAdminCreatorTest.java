package com.paulcartagena.icarapi.user.service;

import com.paulcartagena.icarapi.user.entity.AppUser;
import com.paulcartagena.icarapi.user.enums.UserRole;
import com.paulcartagena.icarapi.user.enums.UserStatus;
import com.paulcartagena.icarapi.user.repository.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InitialAdminCreatorTest {

    private static final String VALID_PASSWORD = "a-strong-password";

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private InitialAdminCreator creator(String username, String password, String fullName) {
        return new InitialAdminCreator(appUserRepository, passwordEncoder, username, password, fullName);
    }

    @Test
    void doesNothingWhenAnAdminAlreadyExists() {
        when(appUserRepository.existsByRole(UserRole.ADMIN)).thenReturn(true);

        // Credentials are not required once an admin exists
        creator("", "", "").run();

        verify(appUserRepository, never()).save(any());
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void createsActiveAdminWithHashedPassword() {
        when(appUserRepository.existsByRole(UserRole.ADMIN)).thenReturn(false);
        when(passwordEncoder.encode(VALID_PASSWORD)).thenReturn("hashed");

        creator("  paul  ", VALID_PASSWORD, "Paul Cartagena").run();

        ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        verify(appUserRepository).save(captor.capture());
        AppUser admin = captor.getValue();
        assertThat(admin.getUsername()).isEqualTo("paul");
        assertThat(admin.getPasswordHash()).isEqualTo("hashed");
        assertThat(admin.getFullName()).isEqualTo("Paul Cartagena");
        assertThat(admin.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(admin.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void defaultsFullNameWhenBlank() {
        when(appUserRepository.existsByRole(UserRole.ADMIN)).thenReturn(false);
        when(passwordEncoder.encode(VALID_PASSWORD)).thenReturn("hashed");

        creator("paul", VALID_PASSWORD, " ").run();

        ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        verify(appUserRepository).save(captor.capture());
        assertThat(captor.getValue().getFullName()).isEqualTo("Administrator");
    }

    @Test
    void failsWhenNoAdminExistsAndCredentialsAreMissing() {
        when(appUserRepository.existsByRole(UserRole.ADMIN)).thenReturn(false);

        assertThatThrownBy(() -> creator("", "", "").run())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ADMIN_USERNAME and ADMIN_PASSWORD");
        verify(appUserRepository, never()).save(any());
    }

    @Test
    void failsWhenPasswordIsTooShort() {
        when(appUserRepository.existsByRole(UserRole.ADMIN)).thenReturn(false);

        assertThatThrownBy(() -> creator("paul", "short", "").run())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ADMIN_PASSWORD");
        verify(appUserRepository, never()).save(any());
    }

    @Test
    void failsWhenUsernameIsTooLong() {
        when(appUserRepository.existsByRole(UserRole.ADMIN)).thenReturn(false);

        assertThatThrownBy(() -> creator("a".repeat(26), VALID_PASSWORD, "").run())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ADMIN_USERNAME");
        verify(appUserRepository, never()).save(any());
    }
}
