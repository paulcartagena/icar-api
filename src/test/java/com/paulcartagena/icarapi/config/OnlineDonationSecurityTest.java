package com.paulcartagena.icarapi.config;

import com.paulcartagena.icarapi.donation.controller.PublicOnlineDonationController;
import com.paulcartagena.icarapi.donation.dto.PayPalOrderResponse;
import com.paulcartagena.icarapi.donation.service.OnlineDonationService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// The PayPal checkout endpoints are public (no token, stale tokens ignored) and creating orders is rate limited per
// client IP. The buckets live in the filter for the whole context, so each test uses its own client IP.
@WebMvcTest(controllers = PublicOnlineDonationController.class, properties = {
        "jwt.secret=test-secret-with-at-least-32-characters!!",
        "paypal.order-rate-limit.capacity=2",
        "paypal.order-rate-limit.refill-period=1h"
})
@Import(SecurityConfig.class)
class OnlineDonationSecurityTest {

    private static final String ORDER = """
            {"amount": 25.00, "donorName": "Ana"}""";
    private static final String EVENT = """
            {"id":"WH-1","event_type":"PAYMENT.CAPTURE.COMPLETED","resource":{"amount":{"value":"25.00"}}}""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OnlineDonationService onlineDonationService;

    @Test
    void configNeedsNoToken() throws Exception {
        mockMvc.perform(get("/public/donations/paypal/config")).andExpect(status().isOk());
    }

    @Test
    void creatingAndCapturingOrdersNeedNoTokenAndIgnoreAStaleOne() throws Exception {
        when(onlineDonationService.createOrder(any())).thenReturn(new PayPalOrderResponse("ORDER-1"));

        createOrder("10.0.2.1", ORDER)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").value("ORDER-1"));
        mockMvc.perform(post("/public/donations/paypal/orders/ORDER-1/capture")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-valid-token"))
                .andExpect(status().isOk());
    }

    @Test
    void anInvalidOrderIs400() throws Exception {
        createOrder("10.0.2.2", """
                {"amount": 25.123}""").andExpect(status().isBadRequest());
        createOrder("10.0.2.2", """
                {"donorName": "Ana"}""").andExpect(status().isBadRequest());
    }

    @Test
    void creatingOrdersOverTheLimitReturns429WithRetryAfter() throws Exception {
        createOrder("10.0.2.3", ORDER).andExpect(status().isCreated());
        createOrder("10.0.2.3", ORDER).andExpect(status().isCreated());

        createOrder("10.0.2.3", ORDER)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.message").value("Too many donation attempts. Try again later."));

        // Capturing isn't limited: it only works for an order the donor already approved
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/public/donations/paypal/orders/ORDER-1/capture").with(request -> {
                request.setRemoteAddr("10.0.2.3");
                return request;
            })).andExpect(status().isOk());
        }
    }

    @Test
    void theWebhookPassesTheRawBodyAndPayPalHeaders() throws Exception {
        mockMvc.perform(post("/public/donations/paypal/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("PAYPAL-AUTH-ALGO", "SHA256withRSA")
                        .header("PAYPAL-CERT-URL", "https://api.paypal.com/cert")
                        .header("PAYPAL-TRANSMISSION-ID", "t-id")
                        .header("PAYPAL-TRANSMISSION-SIG", "t-sig")
                        .header("PAYPAL-TRANSMISSION-TIME", "2026-11-03T12:00:00Z")
                        .content(EVENT))
                .andExpect(status().isOk());

        verify(onlineDonationService).handleWebhook(any(), eq(EVENT));
    }

    @Test
    void aWebhookWithoutPayPalHeadersIs400() throws Exception {
        mockMvc.perform(post("/public/donations/paypal/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(EVENT))
                .andExpect(status().isBadRequest());
    }

    private ResultActions createOrder(String remoteAddr, String body) throws Exception {
        return mockMvc.perform(post("/public/donations/paypal/orders")
                .with(request -> {
                    request.setRemoteAddr(remoteAddr);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }
}
