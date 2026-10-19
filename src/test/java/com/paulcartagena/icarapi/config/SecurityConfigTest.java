package com.paulcartagena.icarapi.config;

import com.paulcartagena.icarapi.category.controller.CategoryController;
import com.paulcartagena.icarapi.category.service.CategoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Any protected endpoint works here; /categories is just a convenient one
@WebMvcTest(controllers = CategoryController.class, properties = "jwt.secret=" + SecurityConfigTest.SECRET)
@Import(SecurityConfig.class)
class SecurityConfigTest {

    static final String SECRET = "test-secret-with-at-least-32-characters!!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @MockitoBean
    private CategoryService categoryService;

    @Test
    void requestWithoutTokenReturns401AsErrorResponse() throws Exception {
        mockMvc.perform(get("/categories"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Authentication required."));
    }

    @Test
    void malformedTokenReturns401() throws Exception {
        mockMvc.perform(get("/categories").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid or expired access token."));
    }

    @Test
    void tokenSignedByOurEncoderIsAccepted() throws Exception {
        String token = encode(jwtEncoder, Instant.now().plusSeconds(60));

        mockMvc.perform(get("/categories").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void expiredTokenReturns401() throws Exception {
        // Past the decoder's default 60s clock skew
        String token = encode(jwtEncoder, Instant.now().minusSeconds(120));

        mockMvc.perform(get("/categories").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid or expired access token."));
    }

    @Test
    void tokenSignedWithAnotherSecretReturns401() throws Exception {
        String otherSecret = "another-secret-with-at-least-32-characters";
        JwtEncoder otherEncoder = NimbusJwtEncoder
                .withSecretKey(new SecretKeySpec(otherSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"))
                .build();
        String token = encode(otherEncoder, Instant.now().plusSeconds(60));

        mockMvc.perform(get("/categories").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    private static String encode(JwtEncoder encoder, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject("00000000-0000-0000-0000-000000000001")
                .issuedAt(expiresAt.minusSeconds(900))
                .expiresAt(expiresAt)
                .claim("permissions", List.of("FINANCE_READ"))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();

        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
