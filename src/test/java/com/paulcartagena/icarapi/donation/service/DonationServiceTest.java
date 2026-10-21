package com.paulcartagena.icarapi.donation.service;

import com.paulcartagena.icarapi.category.entity.Category;
import com.paulcartagena.icarapi.category.enums.CategoryType;
import com.paulcartagena.icarapi.category.repository.CategoryRepository;
import com.paulcartagena.icarapi.donation.dto.DonationRequest;
import com.paulcartagena.icarapi.donation.dto.DonationResponse;
import com.paulcartagena.icarapi.donation.dto.DonationVoidRequest;
import com.paulcartagena.icarapi.donation.entity.Donation;
import com.paulcartagena.icarapi.donation.enums.DonationMethod;
import com.paulcartagena.icarapi.donation.enums.DonationStatus;
import com.paulcartagena.icarapi.donation.repository.DonationRepository;
import com.paulcartagena.icarapi.exception.ApiException;
import com.paulcartagena.icarapi.income.entity.Income;
import com.paulcartagena.icarapi.income.repository.IncomeRepository;
import com.paulcartagena.icarapi.user.entity.AppUser;
import com.paulcartagena.icarapi.user.repository.AppUserRepository;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DonationServiceTest {

    private static final LocalDate JAN_1 = LocalDate.of(2026, 1, 1);
    private static final LocalDate JAN_31 = LocalDate.of(2026, 1, 31);

    @Mock
    private DonationRepository donationRepository;

    @Mock
    private IncomeRepository incomeRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private AppUserRepository appUserRepository;

    @InjectMocks
    private DonationService donationService;

    @Nested
    class GetAllDonations {

        @Test
        void rejectsOnlyOneDateBound() {
            assertApiException(() -> donationService.getAllDonations(null, null, JAN_1, null), HttpStatus.BAD_REQUEST);
            assertApiException(() -> donationService.getAllDonations(null, null, null, JAN_31), HttpStatus.BAD_REQUEST);
            verifyNoInteractions(donationRepository);
        }

        @Test
        void rejectsFromAfterTo() {
            assertApiException(() -> donationService.getAllDonations(null, null, JAN_31, JAN_1), HttpStatus.BAD_REQUEST);
            verifyNoInteractions(donationRepository);
        }

        @Test
        @SuppressWarnings("unchecked")
        void attachesEachDonationsIncome() {
            Category category = category(CategoryType.INCOME, true);
            Donation withIncome = donation();
            Donation withoutIncome = donation();
            Income income = income(withIncome, category);
            when(donationRepository.findAll(any(Specification.class), any(Sort.class)))
                    .thenReturn(List.of(withIncome, withoutIncome));
            when(incomeRepository.findBySourceDonationIdIn(List.of(withIncome.getId(), withoutIncome.getId())))
                    .thenReturn(List.of(income));

            List<DonationResponse> result = donationService.getAllDonations(null, null, null, null);

            assertThat(result.get(0).incomeId()).isEqualTo(income.getId());
            assertThat(result.get(0).categoryName()).isEqualTo(category.getName());
            assertThat(result.get(1).incomeId()).isNull();
        }

        @Test
        @SuppressWarnings("unchecked")
        void skipsIncomeQueryWhenNoDonations() {
            when(donationRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of());

            assertThat(donationService.getAllDonations(null, null, null, null)).isEmpty();
            verifyNoInteractions(incomeRepository);
        }
    }

    @Nested
    class GetDonationById {

        @Test
        void returnsNotFoundWhenMissing() {
            UUID id = UUID.randomUUID();
            when(donationRepository.findById(id)).thenReturn(Optional.empty());

            assertApiException(() -> donationService.getDonationById(id), HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    class CreateDonation {

        @Test
        void savesCompletedDonationAndItsIncome() {
            Category category = category(CategoryType.INCOME, true);
            when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));
            when(donationRepository.save(any(Donation.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(incomeRepository.save(any(Income.class))).thenAnswer(invocation -> invocation.getArgument(0));

            UUID userId = UUID.randomUUID();
            AppUser user = new AppUser();
            when(appUserRepository.getReferenceById(userId)).thenReturn(user);

            DonationRequest request = new DonationRequest("  María Pérez  ", "  Para el techo  ",
                    new BigDecimal("100.00"), DonationMethod.CASH, JAN_1, category.getId());

            DonationResponse response = donationService.createDonation(request, userId);

            assertThat(response.donorName()).isEqualTo("María Pérez");
            assertThat(response.message()).isEqualTo("Para el techo");
            assertThat(response.status()).isEqualTo(DonationStatus.COMPLETED);
            assertThat(response.completedAt()).isEqualTo(JAN_1.atStartOfDay());
            assertThat(response.categoryId()).isEqualTo(category.getId());

            ArgumentCaptor<Income> captor = ArgumentCaptor.forClass(Income.class);
            verify(incomeRepository).save(captor.capture());
            Income income = captor.getValue();
            assertThat(income.getSourceDonation()).isNotNull();
            assertThat(income.getAmount()).isEqualByComparingTo("100.00");
            assertThat(income.getDate()).isEqualTo(JAN_1);
            assertThat(income.getDescription()).isEqualTo("Donación - María Pérez");
            assertThat(income.getRegisteredBy()).isSameAs(user);
            assertThat(income.getSourceDonation().getRegisteredBy()).isSameAs(user);
        }

        @Test
        void blankDonorNameIsAnonymous() {
            Category category = category(CategoryType.INCOME, true);
            when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));
            when(donationRepository.save(any(Donation.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(incomeRepository.save(any(Income.class))).thenAnswer(invocation -> invocation.getArgument(0));

            DonationRequest request = new DonationRequest("   ", null,
                    new BigDecimal("20.00"), DonationMethod.TRANSFER, JAN_1, category.getId());

            DonationResponse response = donationService.createDonation(request, UUID.randomUUID());

            assertThat(response.donorName()).isEqualTo(DonationService.ANONYMOUS_DONOR);
        }

        @Test
        void rejectsPaypal() {
            DonationRequest request = request(DonationMethod.PAYPAL, UUID.randomUUID());

            assertApiException(() -> donationService.createDonation(request, UUID.randomUUID()), HttpStatus.BAD_REQUEST);
            verifyNoInteractions(categoryRepository, donationRepository, incomeRepository);
        }

        @Test
        void rejectsExpenseCategory() {
            Category category = category(CategoryType.EXPENSE, true);
            when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));

            assertApiException(() -> donationService.createDonation(request(DonationMethod.CASH, category.getId()), UUID.randomUUID()),
                    HttpStatus.BAD_REQUEST);
            verify(donationRepository, never()).save(any());
        }

        @Test
        void rejectsInactiveCategory() {
            Category category = category(CategoryType.INCOME, false);
            when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));

            assertApiException(() -> donationService.createDonation(request(DonationMethod.CASH, category.getId()), UUID.randomUUID()),
                    HttpStatus.BAD_REQUEST);
            verify(donationRepository, never()).save(any());
        }

        @Test
        void rejectsUnknownCategory() {
            UUID categoryId = UUID.randomUUID();
            when(categoryRepository.findById(categoryId)).thenReturn(Optional.empty());

            assertApiException(() -> donationService.createDonation(request(DonationMethod.CASH, categoryId), UUID.randomUUID()),
                    HttpStatus.NOT_FOUND);
            verify(donationRepository, never()).save(any());
        }
    }

    @Nested
    class VoidDonation {

        @Test
        void voidsDonationAndItsIncome() {
            Donation donation = donation();
            Income income = income(donation, category(CategoryType.INCOME, true));
            when(donationRepository.findById(donation.getId())).thenReturn(Optional.of(donation));
            when(incomeRepository.findBySourceDonationId(donation.getId())).thenReturn(Optional.of(income));

            LocalDateTime before = LocalDateTime.now();
            DonationResponse response = donationService.voidDonation(donation.getId(), new DonationVoidRequest("  Monto equivocado  "));

            assertThat(response.voided()).isTrue();
            assertThat(response.voidReason()).isEqualTo("Monto equivocado");
            assertThat(response.voidedAt()).isAfterOrEqualTo(before);
            assertThat(income.isVoided()).isTrue();
            assertThat(income.getVoidReason()).isEqualTo("Monto equivocado");
            assertThat(income.getVoidedAt()).isEqualTo(donation.getVoidedAt());
        }

        @Test
        void rejectsAlreadyVoidedDonation() {
            Donation donation = donation();
            donation.setVoided(true);
            when(donationRepository.findById(donation.getId())).thenReturn(Optional.of(donation));

            assertApiException(() -> donationService.voidDonation(donation.getId(), new DonationVoidRequest("Otra vez")),
                    HttpStatus.CONFLICT);
            verifyNoInteractions(incomeRepository);
        }

        @Test
        void returnsNotFoundWhenMissing() {
            UUID id = UUID.randomUUID();
            when(donationRepository.findById(id)).thenReturn(Optional.empty());

            assertApiException(() -> donationService.voidDonation(id, new DonationVoidRequest("Motivo")), HttpStatus.NOT_FOUND);
        }
    }

    private static void assertApiException(ThrowingCallable call, HttpStatus status) {
        assertThatThrownBy(call)
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(status);
    }

    private static Category category(CategoryType type, boolean active) {
        return new Category(UUID.randomUUID(), "Ofrendas", type, active);
    }

    private static Donation donation() {
        Donation donation = new Donation();
        donation.setId(UUID.randomUUID());
        donation.setDonorName("María Pérez");
        donation.setAmount(new BigDecimal("100.00"));
        donation.setMethod(DonationMethod.CASH);
        donation.setStatus(DonationStatus.COMPLETED);
        donation.setCompletedAt(JAN_1.atStartOfDay());
        return donation;
    }

    private static Income income(Donation donation, Category category) {
        Income income = new Income();
        income.setId(UUID.randomUUID());
        income.setCategory(category);
        income.setSourceDonation(donation);
        income.setAmount(donation.getAmount());
        income.setDate(JAN_1);
        return income;
    }

    private static DonationRequest request(DonationMethod method, UUID categoryId) {
        return new DonationRequest(null, null, new BigDecimal("20.00"), method, JAN_1, categoryId);
    }
}
