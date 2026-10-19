package com.paulcartagena.icarapi.auth.service;

import com.paulcartagena.icarapi.auth.dto.LoginRequest;
import com.paulcartagena.icarapi.auth.dto.MeResponse;
import com.paulcartagena.icarapi.auth.entity.RefreshToken;
import com.paulcartagena.icarapi.auth.repository.RefreshTokenRepository;
import com.paulcartagena.icarapi.config.AuthProperties;
import com.paulcartagena.icarapi.exception.ApiException;
import com.paulcartagena.icarapi.user.entity.AppUser;
import com.paulcartagena.icarapi.user.enums.UserStatus;
import com.paulcartagena.icarapi.user.repository.AppUserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@Transactional
public class AuthService {

    private static final String INVALID_CREDENTIALS = "Invalid username or password.";
    private static final String INVALID_REFRESH_TOKEN = "Invalid or expired refresh token.";

    private final AppUserRepository appUserRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccessTokenService accessTokenService;
    private final AuthProperties authProperties;
    private final SecureRandom secureRandom = new SecureRandom();
    private final String dummyPasswordHash;

    public AuthService(AppUserRepository appUserRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder,
                       AccessTokenService accessTokenService,
                       AuthProperties authProperties) {
        this.appUserRepository = appUserRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.accessTokenService = accessTokenService;
        this.authProperties = authProperties;
        this.dummyPasswordHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    public AuthTokens login(LoginRequest request) {
        Optional<AppUser> user = appUserRepository.findByUsername(request.username().trim());

        // Run BCrypt even when the user doesn't exist, so response time doesn't reveal valid usernames
        String hash = user.map(AppUser::getPasswordHash).orElse(dummyPasswordHash);
        boolean passwordMatches = passwordEncoder.matches(request.password(), hash);

        // Same message for every failure: don't tell an attacker which part was wrong
        if (user.isEmpty() || !passwordMatches || user.get().getStatus() != UserStatus.ACTIVE) {
            throw ApiException.unauthorized(INVALID_CREDENTIALS);
        }

        return issueTokens(user.get());
    }

    // noRollbackFor: when a reused token is detected, revoking the user's tokens must be saved even though
    // the request ends in a 401
    @Transactional(noRollbackFor = ApiException.class)
    public AuthTokens refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw ApiException.unauthorized("Refresh token is missing.");
        }

        RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(hash(rawRefreshToken))
                .orElseThrow(() -> ApiException.unauthorized(INVALID_REFRESH_TOKEN));
        AppUser user = refreshToken.getUser();
        LocalDateTime now = LocalDateTime.now();

        // A rotated token should never come back. If it does, someone else may have a copy: log out everywhere.
        if (refreshToken.isRevoked()) {
            log.warn("Reuse of a revoked refresh token for user {}; revoking all their sessions.", user.getId());
            refreshTokenRepository.revokeAllActiveByUserId(user.getId(), now);
            throw ApiException.unauthorized(INVALID_REFRESH_TOKEN);
        }
        if (refreshToken.isExpired(now)) {
            throw ApiException.unauthorized(INVALID_REFRESH_TOKEN);
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            refreshTokenRepository.revokeAllActiveByUserId(user.getId(), now);
            throw ApiException.unauthorized(INVALID_REFRESH_TOKEN);
        }

        // Rotation: each refresh token works once
        refreshToken.setRevokedAt(now);
        return issueTokens(user);
    }

    // Idempotent: an unknown, expired or already revoked token still counts as logged out
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }

        refreshTokenRepository.findByTokenHash(hash(rawRefreshToken))
                .filter(token -> !token.isRevoked())
                .ifPresent(token -> token.setRevokedAt(LocalDateTime.now()));
    }

    @Transactional(readOnly = true)
    public MeResponse getCurrentUser(UUID userId) {
        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("User no longer exists."));

        return new MeResponse(
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getRole(),
                user.getRole().getPermissions().stream().sorted().toList()
        );
    }

    private AuthTokens issueTokens(AppUser user) {
        String rawRefreshToken = generateRefreshToken();
        LocalDateTime now = LocalDateTime.now();

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setTokenHash(hash(rawRefreshToken));
        refreshToken.setCreatedAt(now);
        refreshToken.setExpiresAt(now.plus(authProperties.refreshTokenTtl()));
        refreshTokenRepository.save(refreshToken);

        return new AuthTokens(
                accessTokenService.createAccessToken(user),
                accessTokenService.getExpiresInSeconds(),
                rawRefreshToken
        );
    }

    // 32 random bytes (256 bits), URL-safe so it can go in a cookie as-is
    private String generateRefreshToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    // SHA-256 instead of BCrypt: the token is already random and long, so a fast hash is safe here,
    // and being deterministic lets us look it up by hash
    private static String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
