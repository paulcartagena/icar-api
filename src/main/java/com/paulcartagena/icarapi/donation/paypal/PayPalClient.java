package com.paulcartagena.icarapi.donation.paypal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.paulcartagena.icarapi.config.PayPalProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Talks to the PayPal REST API (Orders v2 and webhook verification) with plain HTTP calls: only four endpoints are
 * needed, so there is no PayPal SDK. Every call that fails or answers something unexpected throws
 * {@link PayPalException}.
 */
@Slf4j
@Component
public class PayPalClient {

    public static final String CURRENCY = "USD";

    // Renew the access token a bit before it expires, so a request never goes out with an expired one
    private static final long TOKEN_EXPIRY_MARGIN_SECONDS = 60;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

    private final RestClient restClient;
    private final PayPalProperties properties;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    private String accessToken;
    private Instant accessTokenExpiresAt = Instant.EPOCH;

    @Autowired
    public PayPalClient(PayPalProperties properties, JsonMapper jsonMapper) {
        this(RestClient.builder().requestFactory(requestFactory()), properties, jsonMapper, Clock.systemUTC());
    }

    PayPalClient(RestClient.Builder restClientBuilder, PayPalProperties properties, JsonMapper jsonMapper, Clock clock) {
        this.restClient = restClientBuilder.baseUrl(properties.baseUrl()).build();
        this.properties = properties;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    /**
     * Creates an order to be paid in PayPal and returns its id. The donation id goes as {@code custom_id} (shown in
     * the PayPal dashboard) and as the idempotency key, so a retried call can't create a second order.
     */
    public String createOrder(UUID donationId, BigDecimal amount) {
        Map<String, Object> body = Map.of(
                "intent", "CAPTURE",
                "purchase_units", List.of(Map.of(
                        "custom_id", donationId.toString(),
                        "description", "Donación",
                        "amount", Map.of("currency_code", CURRENCY, "value", format(amount)))),
                // A donation ships nothing, so PayPal doesn't ask for an address
                "application_context", Map.of("shipping_preference", "NO_SHIPPING"));

        OrderResponse order = call(() -> restClient.post()
                .uri("/v2/checkout/orders")
                .headers(headers -> authorized(headers, "order-" + donationId))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(OrderResponse.class));

        if (order == null || order.id() == null) {
            throw new PayPalException("PayPal didn't return an order id.");
        }
        return order.id();
    }

    /**
     * Captures (charges) an order the payer already approved in PayPal. The idempotency key makes a retried capture
     * return the first result instead of failing.
     */
    public Capture captureOrder(String orderId) {
        OrderResponse order = call(() -> restClient.post()
                .uri("/v2/checkout/orders/{id}/capture", orderId)
                .headers(headers -> authorized(headers, "capture-" + orderId))
                .contentType(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(OrderResponse.class));

        CaptureResource capture = order == null ? null : order.firstCapture();
        if (capture == null) {
            throw new PayPalException("PayPal didn't return a capture for order " + orderId + ".");
        }
        String payerEmail = order.payer() == null ? null : order.payer().emailAddress();
        return capture.toCapture(payerEmail);
    }

    /**
     * Asks PayPal whether a webhook call really comes from PayPal. The event goes back exactly as received (not
     * re-serialized), since the signature covers the original bytes.
     */
    public boolean verifyWebhookSignature(WebhookHeaders headers, String rawEvent) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("auth_algo", headers.authAlgo());
        fields.put("cert_url", headers.certUrl());
        fields.put("transmission_id", headers.transmissionId());
        fields.put("transmission_sig", headers.transmissionSig());
        fields.put("transmission_time", headers.transmissionTime());
        fields.put("webhook_id", properties.webhookId());

        // {"auth_algo": ..., "webhook_id": ...} + "webhook_event": <raw event>
        String json = jsonMapper.writeValueAsString(fields);
        String body = json.substring(0, json.length() - 1) + ",\"webhook_event\":" + rawEvent.trim() + "}";

        VerificationResponse response = call(() -> restClient.post()
                .uri("/v1/notifications/verify-webhook-signature")
                .headers(h -> authorized(h, null))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(VerificationResponse.class));

        return response != null && "SUCCESS".equals(response.verificationStatus());
    }

    // Returns null when the body isn't a JSON object
    public WebhookEvent parseWebhookEvent(String rawEvent) {
        try {
            return jsonMapper.readValue(rawEvent, WebhookEvent.class);
        } catch (JacksonException e) {
            return null;
        }
    }

    private void authorized(HttpHeaders headers, String requestId) {
        headers.setBearerAuth(accessToken());
        if (requestId != null) {
            headers.set("PayPal-Request-Id", requestId);
        }
    }

    // One token is shared by every request until it is about to expire (PayPal gives them for ~9 hours)
    private synchronized String accessToken() {
        if (accessToken != null && clock.instant().isBefore(accessTokenExpiresAt)) {
            return accessToken;
        }

        TokenResponse token = call(() -> restClient.post()
                .uri("/v1/oauth2/token")
                .headers(headers -> headers.setBasicAuth(properties.clientId(), properties.clientSecret()))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body("grant_type=client_credentials")
                .retrieve()
                .body(TokenResponse.class));

        if (token == null || token.accessToken() == null) {
            throw new PayPalException("PayPal didn't return an access token.");
        }
        accessToken = token.accessToken();
        accessTokenExpiresAt = clock.instant().plusSeconds(Math.max(0, token.expiresIn() - TOKEN_EXPIRY_MARGIN_SECONDS));
        return accessToken;
    }

    private <T> T call(Supplier<T> request) {
        try {
            return request.get();
        } catch (RestClientResponseException e) {
            String issue = issueOf(e.getResponseBodyAsString());
            log.warn("PayPal answered {} ({}): {}", e.getStatusCode().value(), issue, e.getResponseBodyAsString());
            throw new PayPalException("PayPal answered " + e.getStatusCode().value() + ".", e.getStatusCode().value(), issue);
        } catch (RestClientException e) {
            log.warn("PayPal request failed: {}", e.getMessage());
            throw new PayPalException("PayPal request failed.");
        }
    }

    // PayPal errors look like {"name": "UNPROCESSABLE_ENTITY", "details": [{"issue": "INSTRUMENT_DECLINED", ...}]}
    private String issueOf(String errorBody) {
        try {
            ErrorResponse error = jsonMapper.readValue(errorBody, ErrorResponse.class);
            if (error.details() != null && !error.details().isEmpty() && error.details().getFirst().issue() != null) {
                return error.details().getFirst().issue();
            }
            return error.name();
        } catch (JacksonException e) {
            return null;
        }
    }

    // Without timeouts a slow PayPal would keep the request (and the donation's row lock) waiting indefinitely
    private static JdkClientHttpRequestFactory requestFactory() {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(READ_TIMEOUT);
        return factory;
    }

    private static String format(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.UNNECESSARY).toPlainString();
    }

    // --- Results ---

    /**
     * A capture as reported by PayPal. {@code status} is COMPLETED (money received), PENDING (PayPal is reviewing
     * it; a webhook reports the outcome), DECLINED or FAILED.
     */
    public record Capture(String id, String status, BigDecimal amount, String currency, String orderId,
                          String payerEmail) {
    }

    // PAYPAL-* headers of a webhook call
    public record WebhookHeaders(String authAlgo, String certUrl, String transmissionId, String transmissionSig,
                                 String transmissionTime) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WebhookEvent(String id, @JsonProperty("event_type") String eventType, CaptureResource resource) {
    }

    // --- PayPal JSON ---

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CaptureResource(String id, String status, Money amount,
                                  @JsonProperty("supplementary_data") SupplementaryData supplementaryData) {

        public Capture toCapture(String payerEmail) {
            String orderId = supplementaryData == null || supplementaryData.relatedIds() == null
                    ? null : supplementaryData.relatedIds().orderId();
            return new Capture(id, status,
                    amount == null ? null : amount.value(),
                    amount == null ? null : amount.currencyCode(),
                    orderId, payerEmail);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Money(@JsonProperty("currency_code") String currencyCode, BigDecimal value) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SupplementaryData(@JsonProperty("related_ids") RelatedIds relatedIds) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RelatedIds(@JsonProperty("order_id") String orderId) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OrderResponse(String id, String status, Payer payer,
                         @JsonProperty("purchase_units") List<PurchaseUnit> purchaseUnits) {

        CaptureResource firstCapture() {
            if (purchaseUnits == null || purchaseUnits.isEmpty()) {
                return null;
            }
            Payments payments = purchaseUnits.getFirst().payments();
            if (payments == null || payments.captures() == null || payments.captures().isEmpty()) {
                return null;
            }
            return payments.captures().getFirst();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Payer(@JsonProperty("email_address") String emailAddress) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PurchaseUnit(Payments payments) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Payments(List<CaptureResource> captures) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TokenResponse(@JsonProperty("access_token") String accessToken,
                         @JsonProperty("expires_in") long expiresIn) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record VerificationResponse(@JsonProperty("verification_status") String verificationStatus) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ErrorResponse(String name, List<ErrorDetail> details) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ErrorDetail(String issue) {
    }
}
