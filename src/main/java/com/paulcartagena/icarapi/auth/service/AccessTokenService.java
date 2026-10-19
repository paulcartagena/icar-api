package com.paulcartagena.icarapi.auth.service;

import com.paulcartagena.icarapi.config.AuthProperties;
import com.paulcartagena.icarapi.user.entity.AppUser;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class AccessTokenService {

    private final JwtEncoder jwtEncoder;
    private final AuthProperties authProperties;

    public AccessTokenService(JwtEncoder jwtEncoder, AuthProperties authProperties) {
        this.jwtEncoder = jwtEncoder;
        this.authProperties = authProperties;
    }

    public String createAccessToken(AppUser user) {
        Instant now = Instant.now();

        // "permissions" is what SecurityConfig turns into authorities for hasAuthority(...)
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(now.plus(authProperties.accessTokenTtl()))
                .claim("username", user.getUsername())
                .claim("role", user.getRole().name())
                .claim("permissions", user.getRole().getPermissions().stream().map(Enum::name).sorted().toList())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();

        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public long getExpiresInSeconds() {
        return authProperties.accessTokenTtl().toSeconds();
    }
}
