package com.paulcartagena.icarapi.auth.dto;

// The refresh token is not here: it travels in an HttpOnly cookie so JavaScript can't read it
public record AccessTokenResponse(
        String accessToken,
        String tokenType,
        long expiresIn
) {

    public AccessTokenResponse(String accessToken, long expiresIn) {
        this(accessToken, "Bearer", expiresIn);
    }
}
