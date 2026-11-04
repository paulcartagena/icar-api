package com.paulcartagena.icarapi.donation.service;

import com.paulcartagena.icarapi.category.entity.Category;
import com.paulcartagena.icarapi.category.enums.CategoryType;
import com.paulcartagena.icarapi.category.repository.CategoryRepository;
import com.paulcartagena.icarapi.config.PayPalProperties;
import com.paulcartagena.icarapi.donation.dto.OnlineDonationResponse;
import com.paulcartagena.icarapi.donation.dto.PayPalConfigResponse;
import com.paulcartagena.icarapi.donation.dto.PayPalOrderRequest;
import com.paulcartagena.icarapi.donation.dto.PayPalOrderResponse;
import com.paulcartagena.icarapi.donation.entity.Donation;
import com.paulcartagena.icarapi.donation.enums.DonationMethod;
import com.paulcartagena.icarapi.donation.enums.DonationStatus;
import com.paulcartagena.icarapi.donation.paypal.PayPalClient;
import com.paulcartagena.icarapi.donation.paypal.PayPalClient.Capture;
import com.paulcartagena.icarapi.donation.paypal.PayPalClient.WebhookEvent;
import com.paulcartagena.icarapi.donation.paypal.PayPalClient.WebhookHeaders;
import com.paulcartagena.icarapi.donation.paypal.PayPalException;
import com.paulcartagena.icarapi.donation.repository.DonationRepository;
import com.paulcartagena.icarapi.exception.ApiException;
import com.paulcartagena.icarapi.income.entity.Income;
import com.paulcartagena.icarapi.income.repository.IncomeRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

import static com.paulcartagena.icarapi.donation.paypal.PayPalClient.CURRENCY;

/**
 * Online donations through PayPal checkout:
 * <ol>
 *   <li>{@link #createOrder}: the donation is saved as PENDING together with its PayPal order.</li>
 *   <li>The donor approves the payment in PayPal (JS SDK popup on the public website).</li>
 *   <li>{@link #captureOrder}: the website asks to charge it. When PayPal confirms it, the donation is COMPLETED and
 *       gets its income, like a manual one; a declined payment marks it FAILED.</li>
 *   <li>{@link #handleWebhook}: PayPal's notification covers the cases where the website never calls capture (tab
 *       closed) or PayPal decides later (a capture under review).</li>
 * </ol>
 * The capture call and the webhook can both report the same payment, so they lock the donation and only act while it
 * is still PENDING.
 */
@Slf4j
@Service
@Transactional
public class OnlineDonationService {

    public static final String CATEGORY_NAME = "Donaciones online";

    private static final int MAX_PAYER_EMAIL_LENGTH = 150;

    private final DonationRepository donationRepository;
    private final IncomeRepository incomeRepository;
    private final CategoryRepository categoryRepository;
    private final PayPalClient payPalClient;
    private final PayPalProperties properties;

    public OnlineDonationService(DonationRepository donationRepository,
                                 IncomeRepository incomeRepository,
                                 CategoryRepository categoryRepository,
                                 PayPalClient payPalClient,
                                 PayPalProperties properties) {
        this.donationRepository = donationRepository;
        this.incomeRepository = incomeRepository;
        this.categoryRepository = categoryRepository;
        this.payPalClient = payPalClient;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public PayPalConfigResponse getConfig() {
        requireConfigured();
        return new PayPalConfigResponse(properties.clientId(), CURRENCY, properties.minAmount(),
                properties.maxAmount(), properties.presetAmounts());
    }

    public PayPalOrderResponse createOrder(PayPalOrderRequest request) {
        requireConfigured();

        if (request.amount().compareTo(properties.minAmount()) < 0
                || request.amount().compareTo(properties.maxAmount()) > 0) {
            throw ApiException.badRequest("Amount must be between %s and %s %s."
                    .formatted(properties.minAmount(), properties.maxAmount(), CURRENCY));
        }

        Donation donation = new Donation();
        donation.setDonorName(donorNameOrAnonymous(request.donorName()));
        donation.setMessage(normalize(request.message()));
        donation.setAmount(request.amount());
        donation.setMethod(DonationMethod.PAYPAL);
        donation.setStatus(DonationStatus.PENDING);
        // registeredBy stays null: nobody from the church typed it in
        Donation savedDonation = donationRepository.save(donation);

        try {
            // If PayPal fails, the exception rolls the donation back too
            savedDonation.setPaypalOrderId(payPalClient.createOrder(savedDonation.getId(), savedDonation.getAmount()));
        } catch (PayPalException e) {
            throw ApiException.badGateway("Could not create the PayPal order. Try again later.");
        }

        return new PayPalOrderResponse(savedDonation.getPaypalOrderId());
    }

    public OnlineDonationResponse captureOrder(String orderId) {
        requireConfigured();

        Donation donation = donationRepository.findWithLockByPaypalOrderId(orderId)
                .orElseThrow(() -> ApiException.resourceNotFound("Online donation not found for order: " + orderId));

        if (donation.isVoided()) {
            throw ApiException.conflict("Donation is voided.");
        }
        // Already captured (e.g. by the webhook, or a repeated call): same answer, no second charge
        if (donation.getStatus() == DonationStatus.COMPLETED) {
            return buildResponse(donation);
        }
        if (donation.getStatus() == DonationStatus.FAILED) {
            throw ApiException.conflict("This payment was declined. Start a new donation.");
        }

        Capture capture;
        try {
            capture = payPalClient.captureOrder(orderId);
        } catch (PayPalException e) {
            if (e.rejected()) {
                // e.g. INSTRUMENT_DECLINED: the donor can pick another card in PayPal (the JS SDK's actions.restart())
                throw ApiException.conflict("PayPal could not complete the payment"
                        + (e.getIssue() != null ? " (" + e.getIssue() + ")" : "") + ".");
            }
            // Unknown outcome: the donation stays PENDING and the webhook reports what happened
            throw ApiException.badGateway("Could not reach PayPal. Try again later.");
        }

        applyCapture(donation, capture);
        return buildResponse(donation);
    }

    public void handleWebhook(WebhookHeaders headers, String rawEvent) {
        if (!properties.configured() || properties.webhookId().isBlank()) {
            throw ApiException.serviceUnavailable("PayPal webhooks are not configured.");
        }

        WebhookEvent event = payPalClient.parseWebhookEvent(rawEvent);
        if (event == null) {
            throw ApiException.badRequest("Invalid webhook event.");
        }

        boolean valid;
        try {
            valid = payPalClient.verifyWebhookSignature(headers, rawEvent);
        } catch (PayPalException e) {
            // PayPal retries webhooks that don't get a 2xx
            throw ApiException.badGateway("Could not verify the webhook with PayPal.");
        }
        if (!valid) {
            log.warn("Rejected a PayPal webhook with an invalid signature (event {}).", event.id());
            throw ApiException.badRequest("Invalid webhook signature.");
        }

        switch (event.eventType() == null ? "" : event.eventType()) {
            case "PAYMENT.CAPTURE.COMPLETED", "PAYMENT.CAPTURE.DENIED" -> applyWebhookCapture(event);
            // Other events (refunds, reversals...) are handled by hand: voiding the donation from /donations
            default -> log.debug("Ignored PayPal webhook {} ({}).", event.id(), event.eventType());
        }
    }

    // --- Helpers ---

    private void applyWebhookCapture(WebhookEvent event) {
        Capture capture = event.resource() == null ? null : event.resource().toCapture(null);
        if (capture == null || capture.orderId() == null) {
            log.warn("PayPal webhook {} has no order id; ignored.", event.id());
            return;
        }

        Optional<Donation> found = donationRepository.findWithLockByPaypalOrderId(capture.orderId());
        if (found.isEmpty()) {
            log.warn("PayPal webhook {} is for unknown order {}; ignored.", event.id(), capture.orderId());
            return;
        }

        Donation donation = found.get();
        // Already settled by the capture call or an earlier delivery of the same event
        if (donation.isVoided() || donation.getStatus() != DonationStatus.PENDING) {
            return;
        }
        applyCapture(donation, capture);
    }

    private void applyCapture(Donation donation, Capture capture) {
        if (capture.id() != null) {
            donation.setPaypalCaptureId(capture.id());
        }
        if (capture.payerEmail() != null && capture.payerEmail().length() <= MAX_PAYER_EMAIL_LENGTH) {
            donation.setPayerEmail(capture.payerEmail());
        }

        switch (capture.status() == null ? "" : capture.status()) {
            case "COMPLETED" -> {
                // The order was created here with this amount, so a difference means something is wrong:
                // leave it PENDING for someone to review in PayPal instead of recording the wrong amount
                if (!CURRENCY.equals(capture.currency()) || capture.amount() == null
                        || capture.amount().compareTo(donation.getAmount()) != 0) {
                    log.error("PayPal capture {} is {} {} but donation {} expects {} {}; left PENDING for review.",
                            capture.id(), capture.amount(), capture.currency(), donation.getId(),
                            donation.getAmount(), CURRENCY);
                    return;
                }
                complete(donation);
            }
            case "DECLINED", "FAILED" -> donation.setStatus(DonationStatus.FAILED);
            // PENDING: PayPal is reviewing the payment; the webhook reports the outcome
            default -> {
            }
        }
    }

    // Same as a manual donation: COMPLETED with its income, so totals and budgets count it
    private void complete(Donation donation) {
        LocalDateTime now = LocalDateTime.now();
        donation.setStatus(DonationStatus.COMPLETED);
        donation.setCompletedAt(now);

        Income income = new Income();
        income.setCategory(onlineDonationsCategory());
        income.setSourceDonation(donation);
        income.setAmount(donation.getAmount());
        income.setDescription("Donación online - " + donation.getDonorName());
        income.setDate(now.toLocalDate());
        incomeRepository.save(income);
    }

    // Created at startup (OnlineDonationCategoryCreator); also here in case it was configured afterwards. It is used
    // even if someone deactivated it: the money already arrived, so the income has to be recorded.
    private Category onlineDonationsCategory() {
        return categoryRepository.findByNameIgnoreCaseAndType(CATEGORY_NAME, CategoryType.INCOME)
                .orElseGet(() -> categoryRepository.save(newOnlineDonationsCategory()));
    }

    static Category newOnlineDonationsCategory() {
        Category category = new Category();
        category.setName(CATEGORY_NAME);
        category.setType(CategoryType.INCOME);
        category.setActive(true);
        return category;
    }

    private void requireConfigured() {
        if (!properties.configured()) {
            throw ApiException.serviceUnavailable("Online donations are not available.");
        }
    }

    private static String donorNameOrAnonymous(String donorName) {
        String normalized = normalize(donorName);
        return normalized == null ? DonationService.ANONYMOUS_DONOR : normalized;
    }

    private static String normalize(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private static OnlineDonationResponse buildResponse(Donation donation) {
        return new OnlineDonationResponse(donation.getId(), donation.getStatus(), donation.getAmount());
    }
}
