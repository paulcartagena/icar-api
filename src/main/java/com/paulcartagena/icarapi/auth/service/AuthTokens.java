package com.paulcartagena.icarapi.auth.service;

// What AuthService hands to the controller: the access token goes in the body, the refresh token in a cookie
public record AuthTokens(
        String accessToken,
        long accessTokenExpiresIn,
        String refreshToken
) {
}
