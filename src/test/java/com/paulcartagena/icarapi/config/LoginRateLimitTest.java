package com.paulcartagena.icarapi.config;

import com.paulcartagena.icarapi.auth.controller.AuthController;
import com.paulcartagena.icarapi.auth.dto.LoginRequest;
import com.paulcartagena.icarapi.auth.service.AuthService;
import com.paulcartagena.icarapi.auth.service.AuthTokens;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// The buckets live in the filter for the whole context, so each test uses its own client IP
@WebMvcTest(controllers = AuthController.class, properties = {
        "jwt.secret=test-secret-with-at-least-32-characters!!",
        "auth.login-rate-limit.capacity=2",
        "auth.login-rate-limit.refill-period=1h"
})
@Import(SecurityConfig.class)
class LoginRateLimitTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @BeforeEach
    void setUp() {
        when(authService.login(any(LoginRequest.class)))
                .thenReturn(new AuthTokens("access-token", 900, "refresh-token"));
    }

    @Test
    void loginOverTheLimitReturns429WithRetryAfter() throws Exception {
        login("10.0.0.1").andExpect(status().isOk());
        login("10.0.0.1").andExpect(status().isOk());

        login("10.0.0.1")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.message").value("Too many login attempts. Try again later."));
    }

    @Test
    void limitIsPerClientIp() throws Exception {
        login("10.0.0.2").andExpect(status().isOk());
        login("10.0.0.2").andExpect(status().isOk());
        login("10.0.0.2").andExpect(status().isTooManyRequests());

        login("10.0.0.3").andExpect(status().isOk());
    }

    @Test
    void forwardedForHeaderDoesNotBypassTheLimit() throws Exception {
        login("10.0.0.4").andExpect(status().isOk());
        login("10.0.0.4").andExpect(status().isOk());

        login("10.0.0.4", "203.0.113.7").andExpect(status().isTooManyRequests());
    }

    @Test
    void otherAuthEndpointsAreNotLimited() throws Exception {
        login("10.0.0.5").andExpect(status().isOk());
        login("10.0.0.5").andExpect(status().isOk());

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/auth/logout").with(request -> {
                        request.setRemoteAddr("10.0.0.5");
                        return request;
                    }))
                    .andExpect(status().isNoContent());
        }
    }

    private ResultActions login(String remoteAddr) throws Exception {
        return login(remoteAddr, null);
    }

    private ResultActions login(String remoteAddr, String forwardedFor) throws Exception {
        return mockMvc.perform(post("/auth/login")
                .with(request -> {
                    request.setRemoteAddr(remoteAddr);
                    if (forwardedFor != null) {
                        request.addHeader("X-Forwarded-For", forwardedFor);
                    }
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"username": "paul", "password": "a-strong-password"}
                        """));
    }
}
