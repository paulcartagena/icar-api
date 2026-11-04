package com.paulcartagena.icarapi.donation.paypal;

import com.paulcartagena.icarapi.config.PayPalProperties;
import com.paulcartagena.icarapi.donation.paypal.PayPalClient.Capture;
import com.paulcartagena.icarapi.donation.paypal.PayPalClient.WebhookHeaders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.twice;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PayPalClientTest {

    private static final String BASE_URL = "https://api-m.sandbox.paypal.com";
    private static final String TOKEN = """
            {"access_token": "token-1", "token_type": "Bearer", "expires_in": 32400}""";

    private MockRestServiceServer server;
    private PayPalClient client;

    @BeforeEach
    void setUp() {
        PayPalProperties properties = new PayPalProperties("client-id", "client-secret", "webhook-id", BASE_URL,
                new BigDecimal("5.00"), new BigDecimal("5000.00"), List.of(BigDecimal.TEN),
                new PayPalProperties.OrderRateLimit(10, Duration.ofHours(1)));
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new PayPalClient(builder, properties, JsonMapper.builder().build(),
                Clock.fixed(Instant.parse("2026-11-03T12:00:00Z"), ZoneOffset.UTC));
    }

    private void expectToken() {
        server.expect(once(), requestTo(BASE_URL + "/v1/oauth2/token"))
                .andExpect(method(HttpMethod.POST))
                // Basic base64("client-id:client-secret")
                .andExpect(header("Authorization", "Basic Y2xpZW50LWlkOmNsaWVudC1zZWNyZXQ="))
                .andExpect(content().string("grant_type=client_credentials"))
                .andRespond(withSuccess(TOKEN, MediaType.APPLICATION_JSON));
    }

    @Test
    void createOrderSendsTheAmountInUsdWithTheDonationIdAndReturnsTheOrderId() {
        UUID donationId = UUID.randomUUID();
        expectToken();
        server.expect(once(), requestTo(BASE_URL + "/v2/checkout/orders"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer token-1"))
                .andExpect(header("PayPal-Request-Id", "order-" + donationId))
                .andExpect(jsonPath("$.intent").value("CAPTURE"))
                .andExpect(jsonPath("$.purchase_units[0].custom_id").value(donationId.toString()))
                .andExpect(jsonPath("$.purchase_units[0].amount.currency_code").value("USD"))
                .andExpect(jsonPath("$.purchase_units[0].amount.value").value("25.00"))
                .andExpect(jsonPath("$.application_context.shipping_preference").value("NO_SHIPPING"))
                .andRespond(withSuccess("""
                        {"id": "ORDER-1", "status": "CREATED"}""", MediaType.APPLICATION_JSON));

        String orderId = client.createOrder(donationId, new BigDecimal("25"));

        assertThat(orderId).isEqualTo("ORDER-1");
        server.verify();
    }

    @Test
    void theAccessTokenIsReusedWhileItIsValid() {
        expectToken();
        server.expect(twice(), requestTo(BASE_URL + "/v2/checkout/orders"))
                .andExpect(header("Authorization", "Bearer token-1"))
                .andRespond(withSuccess("""
                        {"id": "ORDER-1"}""", MediaType.APPLICATION_JSON));

        client.createOrder(UUID.randomUUID(), BigDecimal.TEN);
        client.createOrder(UUID.randomUUID(), BigDecimal.TEN);

        server.verify();
    }

    @Test
    void captureReturnsTheCaptureAndThePayerEmail() {
        expectToken();
        server.expect(once(), requestTo(BASE_URL + "/v2/checkout/orders/ORDER-1/capture"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("PayPal-Request-Id", "capture-ORDER-1"))
                .andRespond(withSuccess("""
                        {"id": "ORDER-1", "status": "COMPLETED",
                         "payer": {"email_address": "donor@example.com"},
                         "purchase_units": [{"payments": {"captures": [{
                             "id": "CAPTURE-1", "status": "COMPLETED",
                             "amount": {"currency_code": "USD", "value": "25.00"},
                             "supplementary_data": {"related_ids": {"order_id": "ORDER-1"}}}]}}]}""",
                        MediaType.APPLICATION_JSON));

        Capture capture = client.captureOrder("ORDER-1");

        assertThat(capture.id()).isEqualTo("CAPTURE-1");
        assertThat(capture.status()).isEqualTo("COMPLETED");
        assertThat(capture.amount()).isEqualByComparingTo("25.00");
        assertThat(capture.currency()).isEqualTo("USD");
        assertThat(capture.orderId()).isEqualTo("ORDER-1");
        assertThat(capture.payerEmail()).isEqualTo("donor@example.com");
    }

    @Test
    void aDeclinedCaptureThrowsARejectionWithPayPalsIssue() {
        expectToken();
        server.expect(once(), requestTo(BASE_URL + "/v2/checkout/orders/ORDER-1/capture"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"name": "UNPROCESSABLE_ENTITY",
                                 "details": [{"issue": "INSTRUMENT_DECLINED"}]}"""));

        assertThatThrownBy(() -> client.captureOrder("ORDER-1"))
                .isInstanceOfSatisfying(PayPalException.class, e -> {
                    assertThat(e.rejected()).isTrue();
                    assertThat(e.getIssue()).isEqualTo("INSTRUMENT_DECLINED");
                });
    }

    @Test
    void aPayPalServerErrorIsNotARejection() {
        expectToken();
        server.expect(once(), requestTo(BASE_URL + "/v2/checkout/orders/ORDER-1/capture"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> client.captureOrder("ORDER-1"))
                .isInstanceOfSatisfying(PayPalException.class, e -> assertThat(e.rejected()).isFalse());
    }

    @Test
    void webhookVerificationSendsTheEventExactlyAsReceived() {
        // Number formats and key order must survive untouched: the signature covers the original bytes
        String rawEvent = """
                {"id":"WH-1","event_type":"PAYMENT.CAPTURE.COMPLETED","resource":{"amount":{"value":"25.00"}}}""";
        expectToken();
        server.expect(once(), requestTo(BASE_URL + "/v1/notifications/verify-webhook-signature"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.auth_algo").value("SHA256withRSA"))
                .andExpect(jsonPath("$.cert_url").value("https://api.paypal.com/cert"))
                .andExpect(jsonPath("$.transmission_id").value("t-id"))
                .andExpect(jsonPath("$.transmission_sig").value("t-sig"))
                .andExpect(jsonPath("$.transmission_time").value("2026-11-03T12:00:00Z"))
                .andExpect(jsonPath("$.webhook_id").value("webhook-id"))
                .andExpect(content().string(org.hamcrest.Matchers.endsWith(",\"webhook_event\":" + rawEvent + "}")))
                .andRespond(withSuccess("""
                        {"verification_status": "SUCCESS"}""", MediaType.APPLICATION_JSON));

        boolean valid = client.verifyWebhookSignature(new WebhookHeaders("SHA256withRSA",
                "https://api.paypal.com/cert", "t-id", "t-sig", "2026-11-03T12:00:00Z"), rawEvent);

        assertThat(valid).isTrue();
    }

    @Test
    void aFailedWebhookVerificationIsInvalid() {
        expectToken();
        server.expect(once(), requestTo(BASE_URL + "/v1/notifications/verify-webhook-signature"))
                .andRespond(withSuccess("""
                        {"verification_status": "FAILURE"}""", MediaType.APPLICATION_JSON));

        assertThat(client.verifyWebhookSignature(new WebhookHeaders("a", "b", "c", "d", "e"), "{}")).isFalse();
    }

    @Test
    void parseWebhookEventReadsTheCaptureAndItsOrder() {
        PayPalClient.WebhookEvent event = client.parseWebhookEvent("""
                {"id": "WH-1", "event_type": "PAYMENT.CAPTURE.COMPLETED", "resource": {
                    "id": "CAPTURE-1", "status": "COMPLETED", "amount": {"currency_code": "USD", "value": "25.00"},
                    "supplementary_data": {"related_ids": {"order_id": "ORDER-1"}}}}""");

        assertThat(event.eventType()).isEqualTo("PAYMENT.CAPTURE.COMPLETED");
        assertThat(event.resource().toCapture(null).orderId()).isEqualTo("ORDER-1");
        assertThat(client.parseWebhookEvent("not json")).isNull();
    }
}
