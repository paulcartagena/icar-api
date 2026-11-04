package com.paulcartagena.icarapi.donation.service;

import com.paulcartagena.icarapi.category.entity.Category;
import com.paulcartagena.icarapi.category.enums.CategoryType;
import com.paulcartagena.icarapi.category.repository.CategoryRepository;
import com.paulcartagena.icarapi.config.PayPalProperties;
import com.paulcartagena.icarapi.donation.dto.OnlineDonationResponse;
import com.paulcartagena.icarapi.donation.dto.PayPalOrderRequest;
import com.paulcartagena.icarapi.donation.entity.Donation;
import com.paulcartagena.icarapi.donation.enums.DonationMethod;
import com.paulcartagena.icarapi.donation.enums.DonationStatus;
import com.paulcartagena.icarapi.donation.paypal.PayPalClient;
import com.paulcartagena.icarapi.donation.paypal.PayPalClient.Capture;
import com.paulcartagena.icarapi.donation.paypal.PayPalClient.CaptureResource;
import com.paulcartagena.icarapi.donation.paypal.PayPalClient.Money;
import com.paulcartagena.icarapi.donation.paypal.PayPalClient.RelatedIds;
import com.paulcartagena.icarapi.donation.paypal.PayPalClient.SupplementaryData;
import com.paulcartagena.icarapi.donation.paypal.PayPalClient.WebhookEvent;
import com.paulcartagena.icarapi.donation.paypal.PayPalClient.WebhookHeaders;
import com.paulcartagena.icarapi.donation.paypal.PayPalException;
import com.paulcartagena.icarapi.donation.repository.DonationRepository;
import com.paulcartagena.icarapi.exception.ApiException;
import com.paulcartagena.icarapi.income.entity.Income;
import com.paulcartagena.icarapi.income.repository.IncomeRepository;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OnlineDonationServiceTest {

    private static final String ORDER_ID = "ORDER-1";
    private static final WebhookHeaders HEADERS = new WebhookHeaders("algo", "cert", "id", "sig", "time");

    @Mock
    private DonationRepository donationRepository;

    @Mock
    private IncomeRepository incomeRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private PayPalClient payPalClient;

    private OnlineDonationService service;

    @BeforeEach
    void setUp() {
        service = serviceWith(properties("client-id", "client-secret", "webhook-id"));
    }

    private OnlineDonationService serviceWith(PayPalProperties properties) {
        return new OnlineDonationService(donationRepository, incomeRepository, categoryRepository, payPalClient,
                properties);
    }

    private static PayPalProperties properties(String clientId, String clientSecret, String webhookId) {
        return new PayPalProperties(clientId, clientSecret, webhookId, "https://api-m.sandbox.paypal.com",
                new BigDecimal("5.00"), new BigDecimal("5000.00"),
                List.of(new BigDecimal("5"), new BigDecimal("10"), new BigDecimal("25"), new BigDecimal("50"),
                        new BigDecimal("100")),
                new PayPalProperties.OrderRateLimit(10, Duration.ofHours(1)));
    }

    private static Donation pending(String amount) {
        Donation donation = new Donation();
        donation.setId(UUID.randomUUID());
        donation.setDonorName("Ana");
        donation.setAmount(new BigDecimal(amount));
        donation.setMethod(DonationMethod.PAYPAL);
        donation.setStatus(DonationStatus.PENDING);
        donation.setPaypalOrderId(ORDER_ID);
        return donation;
    }

    private static Category onlineCategory() {
        Category category = OnlineDonationService.newOnlineDonationsCategory();
        category.setId(UUID.randomUUID());
        return category;
    }

    private static Capture capture(String status, String amount) {
        return new Capture("CAPTURE-1", status, new BigDecimal(amount), "USD", ORDER_ID, "donor@example.com");
    }

    private static void assertStatus(ThrowingCallable call, HttpStatus status) {
        assertThatThrownBy(call)
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(status);
    }

    @Nested
    class Config {

        @Test
        void returnsTheClientIdLimitsAndPresetAmounts() {
            var config = service.getConfig();

            assertThat(config.clientId()).isEqualTo("client-id");
            assertThat(config.currency()).isEqualTo("USD");
            assertThat(config.minAmount()).isEqualByComparingTo("5");
            assertThat(config.maxAmount()).isEqualByComparingTo("5000");
            assertThat(config.presetAmounts()).extracting(BigDecimal::intValue).containsExactly(5, 10, 25, 50, 100);
        }

        @Test
        void withoutCredentialsEveryEndpointAnswers503() {
            OnlineDonationService unconfigured = serviceWith(properties("", "", ""));

            assertStatus(unconfigured::getConfig, HttpStatus.SERVICE_UNAVAILABLE);
            assertStatus(() -> unconfigured.createOrder(new PayPalOrderRequest(BigDecimal.TEN, null, null)),
                    HttpStatus.SERVICE_UNAVAILABLE);
            assertStatus(() -> unconfigured.captureOrder(ORDER_ID), HttpStatus.SERVICE_UNAVAILABLE);
            assertStatus(() -> unconfigured.handleWebhook(HEADERS, "{}"), HttpStatus.SERVICE_UNAVAILABLE);
            verifyNoInteractions(payPalClient);
        }
    }

    @Nested
    class CreateOrder {

        @Test
        void savesAPendingPayPalDonationWithItsOrder() {
            when(donationRepository.save(any(Donation.class))).thenAnswer(invocation -> {
                Donation donation = invocation.getArgument(0);
                donation.setId(UUID.randomUUID());
                return donation;
            });
            when(payPalClient.createOrder(any(UUID.class), any(BigDecimal.class))).thenReturn(ORDER_ID);

            var response = service.createOrder(new PayPalOrderRequest(new BigDecimal("25.00"), " Ana ", " Bendiciones "));

            ArgumentCaptor<Donation> saved = ArgumentCaptor.forClass(Donation.class);
            verify(donationRepository).save(saved.capture());
            Donation donation = saved.getValue();
            assertThat(response.orderId()).isEqualTo(ORDER_ID);
            assertThat(donation.getPaypalOrderId()).isEqualTo(ORDER_ID);
            assertThat(donation.getStatus()).isEqualTo(DonationStatus.PENDING);
            assertThat(donation.getMethod()).isEqualTo(DonationMethod.PAYPAL);
            assertThat(donation.getDonorName()).isEqualTo("Ana");
            assertThat(donation.getMessage()).isEqualTo("Bendiciones");
            assertThat(donation.getRegisteredBy()).isNull();
            assertThat(donation.getCompletedAt()).isNull();
            verify(payPalClient).createOrder(donation.getId(), new BigDecimal("25.00"));
            verifyNoInteractions(incomeRepository);
        }

        @Test
        void blankDonorNameIsAnonymous() {
            when(donationRepository.save(any(Donation.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(payPalClient.createOrder(any(), any())).thenReturn(ORDER_ID);

            service.createOrder(new PayPalOrderRequest(BigDecimal.TEN, "  ", null));

            ArgumentCaptor<Donation> saved = ArgumentCaptor.forClass(Donation.class);
            verify(donationRepository).save(saved.capture());
            assertThat(saved.getValue().getDonorName()).isEqualTo("Anónimo");
        }

        @Test
        void acceptsTheLimitsThemselves() {
            when(donationRepository.save(any(Donation.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(payPalClient.createOrder(any(), any())).thenReturn(ORDER_ID);

            service.createOrder(new PayPalOrderRequest(new BigDecimal("5.00"), null, null));
            service.createOrder(new PayPalOrderRequest(new BigDecimal("5000"), null, null));
        }

        @Test
        void amountsOutsideTheLimitsAre400() {
            assertStatus(() -> service.createOrder(new PayPalOrderRequest(new BigDecimal("4.99"), null, null)),
                    HttpStatus.BAD_REQUEST);
            assertStatus(() -> service.createOrder(new PayPalOrderRequest(new BigDecimal("5000.01"), null, null)),
                    HttpStatus.BAD_REQUEST);
            verifyNoInteractions(donationRepository, payPalClient);
        }

        @Test
        void aPayPalFailureIs502() {
            when(donationRepository.save(any(Donation.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(payPalClient.createOrder(any(), any())).thenThrow(new PayPalException("down"));

            assertStatus(() -> service.createOrder(new PayPalOrderRequest(BigDecimal.TEN, null, null)),
                    HttpStatus.BAD_GATEWAY);
        }
    }

    @Nested
    class CaptureOrder {

        @Test
        void aCompletedCaptureCompletesTheDonationAndCreatesItsIncome() {
            Donation donation = pending("25.00");
            Category category = onlineCategory();
            when(donationRepository.findWithLockByPaypalOrderId(ORDER_ID)).thenReturn(Optional.of(donation));
            when(payPalClient.captureOrder(ORDER_ID)).thenReturn(capture("COMPLETED", "25.00"));
            when(categoryRepository.findByNameIgnoreCaseAndType("Donaciones online", CategoryType.INCOME))
                    .thenReturn(Optional.of(category));

            OnlineDonationResponse response = service.captureOrder(ORDER_ID);

            assertThat(response.status()).isEqualTo(DonationStatus.COMPLETED);
            assertThat(donation.getCompletedAt()).isNotNull();
            assertThat(donation.getPaypalCaptureId()).isEqualTo("CAPTURE-1");
            assertThat(donation.getPayerEmail()).isEqualTo("donor@example.com");

            ArgumentCaptor<Income> income = ArgumentCaptor.forClass(Income.class);
            verify(incomeRepository).save(income.capture());
            assertThat(income.getValue().getCategory()).isSameAs(category);
            assertThat(income.getValue().getSourceDonation()).isSameAs(donation);
            assertThat(income.getValue().getAmount()).isEqualByComparingTo("25.00");
            assertThat(income.getValue().getDate()).isEqualTo(LocalDate.now());
            assertThat(income.getValue().getRegisteredBy()).isNull();
        }

        @Test
        void theCategoryIsCreatedIfItDoesNotExistYet() {
            when(donationRepository.findWithLockByPaypalOrderId(ORDER_ID)).thenReturn(Optional.of(pending("25.00")));
            when(payPalClient.captureOrder(ORDER_ID)).thenReturn(capture("COMPLETED", "25.00"));
            when(categoryRepository.findByNameIgnoreCaseAndType(anyString(), eq(CategoryType.INCOME)))
                    .thenReturn(Optional.empty());
            when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));

            service.captureOrder(ORDER_ID);

            ArgumentCaptor<Category> category = ArgumentCaptor.forClass(Category.class);
            verify(categoryRepository).save(category.capture());
            assertThat(category.getValue().getName()).isEqualTo("Donaciones online");
            assertThat(category.getValue().getType()).isEqualTo(CategoryType.INCOME);
            assertThat(category.getValue().isActive()).isTrue();
        }

        @Test
        void aDeclinedCaptureMarksItFailedWithoutIncome() {
            Donation donation = pending("25.00");
            when(donationRepository.findWithLockByPaypalOrderId(ORDER_ID)).thenReturn(Optional.of(donation));
            when(payPalClient.captureOrder(ORDER_ID)).thenReturn(capture("DECLINED", "25.00"));

            OnlineDonationResponse response = service.captureOrder(ORDER_ID);

            assertThat(response.status()).isEqualTo(DonationStatus.FAILED);
            verifyNoInteractions(incomeRepository);
        }

        @Test
        void aCaptureUnderReviewStaysPending() {
            Donation donation = pending("25.00");
            when(donationRepository.findWithLockByPaypalOrderId(ORDER_ID)).thenReturn(Optional.of(donation));
            when(payPalClient.captureOrder(ORDER_ID)).thenReturn(capture("PENDING", "25.00"));

            OnlineDonationResponse response = service.captureOrder(ORDER_ID);

            assertThat(response.status()).isEqualTo(DonationStatus.PENDING);
            assertThat(donation.getPaypalCaptureId()).isEqualTo("CAPTURE-1");
            verifyNoInteractions(incomeRepository);
        }

        @Test
        void aDifferentAmountIsLeftPendingForReview() {
            Donation donation = pending("25.00");
            when(donationRepository.findWithLockByPaypalOrderId(ORDER_ID)).thenReturn(Optional.of(donation));
            when(payPalClient.captureOrder(ORDER_ID)).thenReturn(capture("COMPLETED", "2.50"));

            assertThat(service.captureOrder(ORDER_ID).status()).isEqualTo(DonationStatus.PENDING);
            verifyNoInteractions(incomeRepository);
        }

        @Test
        void anAlreadyCompletedDonationIsReturnedWithoutChargingAgain() {
            Donation donation = pending("25.00");
            donation.setStatus(DonationStatus.COMPLETED);
            when(donationRepository.findWithLockByPaypalOrderId(ORDER_ID)).thenReturn(Optional.of(donation));

            assertThat(service.captureOrder(ORDER_ID).status()).isEqualTo(DonationStatus.COMPLETED);
            verify(payPalClient, never()).captureOrder(anyString());
        }

        @Test
        void failedOrVoidedDonationsAre409() {
            Donation failed = pending("25.00");
            failed.setStatus(DonationStatus.FAILED);
            when(donationRepository.findWithLockByPaypalOrderId(ORDER_ID)).thenReturn(Optional.of(failed));
            assertStatus(() -> service.captureOrder(ORDER_ID), HttpStatus.CONFLICT);

            Donation voided = pending("25.00");
            voided.setVoided(true);
            when(donationRepository.findWithLockByPaypalOrderId(ORDER_ID)).thenReturn(Optional.of(voided));
            assertStatus(() -> service.captureOrder(ORDER_ID), HttpStatus.CONFLICT);

            verify(payPalClient, never()).captureOrder(anyString());
        }

        @Test
        void anUnknownOrderIs404() {
            when(donationRepository.findWithLockByPaypalOrderId(ORDER_ID)).thenReturn(Optional.empty());

            assertStatus(() -> service.captureOrder(ORDER_ID), HttpStatus.NOT_FOUND);
        }

        @Test
        void aPayPalRejectionIs409AndKeepsItPending() {
            Donation donation = pending("25.00");
            when(donationRepository.findWithLockByPaypalOrderId(ORDER_ID)).thenReturn(Optional.of(donation));
            when(payPalClient.captureOrder(ORDER_ID))
                    .thenThrow(new PayPalException("declined", 422, "INSTRUMENT_DECLINED"));

            assertThatThrownBy(() -> service.captureOrder(ORDER_ID))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("INSTRUMENT_DECLINED");
            assertThat(donation.getStatus()).isEqualTo(DonationStatus.PENDING);
        }

        @Test
        void anUnreachablePayPalIs502AndKeepsItPending() {
            Donation donation = pending("25.00");
            when(donationRepository.findWithLockByPaypalOrderId(ORDER_ID)).thenReturn(Optional.of(donation));
            when(payPalClient.captureOrder(ORDER_ID)).thenThrow(new PayPalException("timeout"));

            assertStatus(() -> service.captureOrder(ORDER_ID), HttpStatus.BAD_GATEWAY);
            assertThat(donation.getStatus()).isEqualTo(DonationStatus.PENDING);
        }
    }

    @Nested
    class Webhook {

        private static final String RAW = "{\"id\":\"WH-1\"}";

        private WebhookEvent event(String type, String captureStatus, String amount) {
            return new WebhookEvent("WH-1", type, new CaptureResource("CAPTURE-1", captureStatus,
                    new Money("USD", new BigDecimal(amount)), new SupplementaryData(new RelatedIds(ORDER_ID))));
        }

        @Test
        void aCompletedCaptureCompletesAPendingDonation() {
            Donation donation = pending("25.00");
            when(payPalClient.parseWebhookEvent(RAW)).thenReturn(event("PAYMENT.CAPTURE.COMPLETED", "COMPLETED", "25.00"));
            when(payPalClient.verifyWebhookSignature(HEADERS, RAW)).thenReturn(true);
            when(donationRepository.findWithLockByPaypalOrderId(ORDER_ID)).thenReturn(Optional.of(donation));
            when(categoryRepository.findByNameIgnoreCaseAndType(anyString(), eq(CategoryType.INCOME)))
                    .thenReturn(Optional.of(onlineCategory()));

            service.handleWebhook(HEADERS, RAW);

            assertThat(donation.getStatus()).isEqualTo(DonationStatus.COMPLETED);
            assertThat(donation.getPaypalCaptureId()).isEqualTo("CAPTURE-1");
            verify(incomeRepository).save(any(Income.class));
        }

        @Test
        void aDeniedCaptureMarksItFailed() {
            Donation donation = pending("25.00");
            when(payPalClient.parseWebhookEvent(RAW)).thenReturn(event("PAYMENT.CAPTURE.DENIED", "DECLINED", "25.00"));
            when(payPalClient.verifyWebhookSignature(HEADERS, RAW)).thenReturn(true);
            when(donationRepository.findWithLockByPaypalOrderId(ORDER_ID)).thenReturn(Optional.of(donation));

            service.handleWebhook(HEADERS, RAW);

            assertThat(donation.getStatus()).isEqualTo(DonationStatus.FAILED);
            verifyNoInteractions(incomeRepository);
        }

        @Test
        void aRepeatedEventForACompletedDonationDoesNothing() {
            Donation donation = pending("25.00");
            donation.setStatus(DonationStatus.COMPLETED);
            when(payPalClient.parseWebhookEvent(RAW)).thenReturn(event("PAYMENT.CAPTURE.COMPLETED", "COMPLETED", "25.00"));
            when(payPalClient.verifyWebhookSignature(HEADERS, RAW)).thenReturn(true);
            when(donationRepository.findWithLockByPaypalOrderId(ORDER_ID)).thenReturn(Optional.of(donation));

            service.handleWebhook(HEADERS, RAW);

            verifyNoInteractions(incomeRepository);
        }

        @Test
        void anInvalidSignatureIs400AndChangesNothing() {
            when(payPalClient.parseWebhookEvent(RAW)).thenReturn(event("PAYMENT.CAPTURE.COMPLETED", "COMPLETED", "25.00"));
            when(payPalClient.verifyWebhookSignature(HEADERS, RAW)).thenReturn(false);

            assertStatus(() -> service.handleWebhook(HEADERS, RAW), HttpStatus.BAD_REQUEST);
            verifyNoInteractions(donationRepository, incomeRepository);
        }

        @Test
        void aBodyThatIsNotAnEventIs400() {
            when(payPalClient.parseWebhookEvent("nope")).thenReturn(null);

            assertStatus(() -> service.handleWebhook(HEADERS, "nope"), HttpStatus.BAD_REQUEST);
            verify(payPalClient, never()).verifyWebhookSignature(any(), anyString());
        }

        @Test
        void otherEventsAndUnknownOrdersAreIgnored() {
            when(payPalClient.parseWebhookEvent(RAW)).thenReturn(event("PAYMENT.CAPTURE.REFUNDED", "REFUNDED", "25.00"));
            when(payPalClient.verifyWebhookSignature(HEADERS, RAW)).thenReturn(true);
            service.handleWebhook(HEADERS, RAW);
            verifyNoInteractions(donationRepository);

            when(payPalClient.parseWebhookEvent(RAW)).thenReturn(event("PAYMENT.CAPTURE.COMPLETED", "COMPLETED", "25.00"));
            when(donationRepository.findWithLockByPaypalOrderId(ORDER_ID)).thenReturn(Optional.empty());
            service.handleWebhook(HEADERS, RAW);
            verifyNoInteractions(incomeRepository);
        }

        @Test
        void withoutAWebhookIdItIs503() {
            OnlineDonationService noWebhook = serviceWith(properties("client-id", "client-secret", ""));

            assertStatus(() -> noWebhook.handleWebhook(HEADERS, RAW), HttpStatus.SERVICE_UNAVAILABLE);
        }
    }
}
