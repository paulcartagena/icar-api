package com.paulcartagena.icarapi.user.service;

import com.paulcartagena.icarapi.user.entity.AppUser;
import com.paulcartagena.icarapi.user.enums.UserRole;
import com.paulcartagena.icarapi.user.enums.UserStatus;
import com.paulcartagena.icarapi.user.repository.AppUserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// There is no public sign-up, so the first ADMIN is created at startup from env vars. Once an admin exists
// this does nothing, and ADMIN_USERNAME/ADMIN_PASSWORD can be removed from the environment.
@Slf4j
@Component
public class InitialAdminCreator implements CommandLineRunner {

    static final int MIN_PASSWORD_LENGTH = 12;
    // BCrypt only uses the first 72 bytes and Spring Security rejects longer passwords
    static final int MAX_PASSWORD_LENGTH = 72;
    static final int MAX_USERNAME_LENGTH = 25;

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final String username;
    private final String password;
    private final String fullName;

    public InitialAdminCreator(AppUserRepository appUserRepository,
                               PasswordEncoder passwordEncoder,
                               @Value("${admin.username:}") String username,
                               @Value("${admin.password:}") String password,
                               @Value("${admin.full-name:}") String fullName) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.username = username.trim();
        this.password = password;
        this.fullName = fullName.isBlank() ? "Administrator" : fullName.trim();
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (appUserRepository.existsByRole(UserRole.ADMIN)) {
            return;
        }

        // Fail fast: without an admin nobody can log in or create other users
        if (username.isEmpty() || password.isBlank()) {
            throw new IllegalStateException(
                    "No ADMIN user exists. Set ADMIN_USERNAME and ADMIN_PASSWORD to create the initial admin.");
        }
        if (username.length() > MAX_USERNAME_LENGTH) {
            throw new IllegalStateException(
                    "ADMIN_USERNAME must be at most " + MAX_USERNAME_LENGTH + " characters.");
        }
        if (password.length() < MIN_PASSWORD_LENGTH || password.length() > MAX_PASSWORD_LENGTH) {
            throw new IllegalStateException(
                    "ADMIN_PASSWORD must be between " + MIN_PASSWORD_LENGTH + " and " + MAX_PASSWORD_LENGTH + " characters.");
        }

        AppUser admin = new AppUser();
        admin.setUsername(username);
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setFullName(fullName);
        admin.setRole(UserRole.ADMIN);
        admin.setStatus(UserStatus.ACTIVE);
        appUserRepository.save(admin);

        // Never log the password
        log.info("Created initial admin user '{}'.", username);
    }
}
