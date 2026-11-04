package com.paulcartagena.icarapi.donation.controller;

import com.paulcartagena.icarapi.donation.dto.OnlineDonationResponse;
import com.paulcartagena.icarapi.donation.dto.PayPalConfigResponse;
import com.paulcartagena.icarapi.donation.dto.PayPalOrderRequest;
import com.paulcartagena.icarapi.donation.dto.PayPalOrderResponse;
import com.paulcartagena.icarapi.donation.paypal.PayPalClient.WebhookHeaders;
import com.paulcartagena.icarapi.donation.service.OnlineDonationService;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

// PayPal checkout of the public website, no token needed (PUBLIC_ROUTES in SecurityConfig); creating orders is rate
// limited per client IP. The webhook is called by PayPal itself and is trusted only after verifying its signature.
@RestController
@RequestMapping("/public/donations/paypal")
@Tag(name = "Public")
@SecurityRequirements
public class PublicOnlineDonationController {

    private final OnlineDonationService onlineDonationService;

    public PublicOnlineDonationController(OnlineDonationService onlineDonationService) {
        this.onlineDonationService = onlineDonationService;
    }

    @PreAuthorize("permitAll()")
    @GetMapping("/config")
    public PayPalConfigResponse getConfig() {
        return onlineDonationService.getConfig();
    }

    @PreAuthorize("permitAll()")
    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public PayPalOrderResponse createOrder(@Valid @RequestBody PayPalOrderRequest request) {
        return onlineDonationService.createOrder(request);
    }

    @PreAuthorize("permitAll()")
    @PostMapping("/orders/{orderId}/capture")
    public OnlineDonationResponse captureOrder(@PathVariable String orderId) {
        return onlineDonationService.captureOrder(orderId);
    }

    // The raw body is kept as received: PayPal's signature covers those exact bytes
    @PreAuthorize("permitAll()")
    @PostMapping("/webhook")
    public void handleWebhook(@RequestHeader("PAYPAL-AUTH-ALGO") String authAlgo,
                              @RequestHeader("PAYPAL-CERT-URL") String certUrl,
                              @RequestHeader("PAYPAL-TRANSMISSION-ID") String transmissionId,
                              @RequestHeader("PAYPAL-TRANSMISSION-SIG") String transmissionSig,
                              @RequestHeader("PAYPAL-TRANSMISSION-TIME") String transmissionTime,
                              @RequestBody String rawEvent) {
        onlineDonationService.handleWebhook(
                new WebhookHeaders(authAlgo, certUrl, transmissionId, transmissionSig, transmissionTime), rawEvent);
    }
}
