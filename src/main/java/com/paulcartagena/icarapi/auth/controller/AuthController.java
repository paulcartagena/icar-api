package com.paulcartagena.icarapi.auth.controller;

import com.paulcartagena.icarapi.auth.dto.AccessTokenResponse;
import com.paulcartagena.icarapi.auth.dto.LoginRequest;
import com.paulcartagena.icarapi.auth.dto.MeResponse;
import com.paulcartagena.icarapi.auth.service.AuthService;
import com.paulcartagena.icarapi.auth.service.AuthTokens;
import com.paulcartagena.icarapi.config.AuthProperties;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.UUID;

@RestController
@RequestMapping("/auth")
@Tag(name = "Auth")
public class AuthController {

    static final String REFRESH_COOKIE = "refresh_token";

    private final AuthService authService;
    private final AuthProperties authProperties;

    public AuthController(AuthService authService, AuthProperties authProperties) {
        this.authService = authService;
        this.authProperties = authProperties;
    }

    @PostMapping("/login")
    public ResponseEntity<AccessTokenResponse> login(@Valid @RequestBody LoginRequest loginRequest) {
        return withRefreshCookie(authService.login(loginRequest));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AccessTokenResponse> refresh(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
        return withRefreshCookie(authService.refresh(refreshToken));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
        authService.logout(refreshToken);

        // Max-Age 0 tells the browser to delete the cookie
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshCookie("", Duration.ZERO).toString())
                .build();
    }

    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal Jwt jwt) {
        return authService.getCurrentUser(UUID.fromString(jwt.getSubject()));
    }

    private ResponseEntity<AccessTokenResponse> withRefreshCookie(AuthTokens tokens) {
        ResponseCookie cookie = refreshCookie(tokens.refreshToken(), authProperties.refreshTokenTtl());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(new AccessTokenResponse(tokens.accessToken(), tokens.accessTokenExpiresIn()));
    }

    // HttpOnly: JavaScript can't read it. SameSite=Strict: other sites can't make the browser send it.
    // Path=/auth: the browser only sends it to the auth endpoints, not on every API call.
    private ResponseCookie refreshCookie(String value, Duration maxAge) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(authProperties.refreshCookieSecure())
                .sameSite("Strict")
                .path("/auth")
                .maxAge(maxAge)
                .build();
    }
}
