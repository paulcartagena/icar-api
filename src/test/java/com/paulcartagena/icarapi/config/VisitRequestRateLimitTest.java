package com.paulcartagena.icarapi.config;

import com.paulcartagena.icarapi.visitrequest.controller.PublicVisitRequestController;
import com.paulcartagena.icarapi.visitrequest.service.VisitRequestService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// The buckets live in the filter for the whole context, so each test uses its own client IP
@WebMvcTest(controllers = PublicVisitRequestController.class, properties = {
        "jwt.secret=test-secret-with-at-least-32-characters!!",
        "visit-requests.rate-limit.capacity=2",
        "visit-requests.rate-limit.refill-period=1h",
        "trusted-proxy.secret=website-secret"
})
@Import(SecurityConfig.class)
class VisitRequestRateLimitTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private VisitRequestService visitRequestService;

    @Test
    void visitRequestsOverTheLimitReturn429WithRetryAfter() throws Exception {
        send("10.0.1.1").andExpect(status().isCreated());
        send("10.0.1.1").andExpect(status().isCreated());

        send("10.0.1.1")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.message").value("Too many visit requests. Try again later."));
    }

    @Test
    void limitIsPerClientIp() throws Exception {
        send("10.0.1.2").andExpect(status().isCreated());
        send("10.0.1.2").andExpect(status().isCreated());
        send("10.0.1.2").andExpect(status().isTooManyRequests());

        send("10.0.1.3").andExpect(status().isCreated());
    }

    // The website's server sends every visitor from its own IP: with the shared secret each one gets its own limit
    @Test
    void websiteVisitorsAreLimitedByTheirOwnIp() throws Exception {
        send("10.0.1.4", "website-secret", "203.0.113.1").andExpect(status().isCreated());
        send("10.0.1.4", "website-secret", "203.0.113.1").andExpect(status().isCreated());
        send("10.0.1.4", "website-secret", "203.0.113.1").andExpect(status().isTooManyRequests());

        send("10.0.1.4", "website-secret", "203.0.113.2").andExpect(status().isCreated());
    }

    @Test
    void clientIpHeaderWithoutTheRightSecretIsIgnored() throws Exception {
        send("10.0.1.5", "wrong-secret", "203.0.113.3").andExpect(status().isCreated());
        send("10.0.1.5", null, "203.0.113.4").andExpect(status().isCreated());

        send("10.0.1.5", "wrong-secret", "203.0.113.5").andExpect(status().isTooManyRequests());
    }

    private ResultActions send(String remoteAddr) throws Exception {
        return send(remoteAddr, null, null);
    }

    private ResultActions send(String remoteAddr, String proxySecret, String clientIp) throws Exception {
        return mockMvc.perform(post("/public/visit-requests")
                .with(request -> {
                    request.setRemoteAddr(remoteAddr);
                    if (proxySecret != null) {
                        request.addHeader(ClientIpResolver.SECRET_HEADER, proxySecret);
                    }
                    if (clientIp != null) {
                        request.addHeader(ClientIpResolver.CLIENT_IP_HEADER, clientIp);
                    }
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"fullName": "Ana Pérez", "phone": "60000000", "email": "ana@example.com"}
                        """));
    }
}
