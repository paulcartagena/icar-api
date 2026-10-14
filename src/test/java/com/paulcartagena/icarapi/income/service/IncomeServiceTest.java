package com.paulcartagena.icarapi.income.service;

import com.paulcartagena.icarapi.category.entity.Category;
import com.paulcartagena.icarapi.category.enums.CategoryType;
import com.paulcartagena.icarapi.category.repository.CategoryRepository;
import com.paulcartagena.icarapi.donation.entity.Donation;
import com.paulcartagena.icarapi.exception.ApiException;
import com.paulcartagena.icarapi.income.dto.IncomeCategoryTotal;
import com.paulcartagena.icarapi.income.dto.IncomeRequest;
import com.paulcartagena.icarapi.income.dto.IncomeResponse;
import com.paulcartagena.icarapi.income.dto.IncomeSummaryResponse;
import com.paulcartagena.icarapi.income.dto.IncomeVoidRequest;
import com.paulcartagena.icarapi.income.entity.Income;
import com.paulcartagena.icarapi.income.repository.IncomeRepository;
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
class IncomeServiceTest {

    private static final LocalDate JAN_1 = LocalDate.of(2026, 1, 1);
    private static final LocalDate JAN_31 = LocalDate.of(2026, 1, 31);

    @Mock
    private IncomeRepository incomeRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private IncomeService incomeService;

    @Nested
    class GetAllIncomes {

        @Test
        void rejectsOnlyOneDateBound() {
            assertApiException(() -> incomeService.getAllIncomes(null, JAN_1, null), HttpStatus.BAD_REQUEST);
            assertApiException(() -> incomeService.getAllIncomes(null, null, JAN_31), HttpStatus.BAD_REQUEST);
            verifyNoInteractions(incomeRepository);
        }

        @Test
        void rejectsFromAfterTo() {
            assertApiException(() -> incomeService.getAllIncomes(null, JAN_31, JAN_1), HttpStatus.BAD_REQUEST);
            verifyNoInteractions(incomeRepository);
        }

        @Test
        @SuppressWarnings("unchecked")
        void returnsIncomesSortedByDateDescending() {
            Income income = income(category(CategoryType.INCOME, true));
            when(incomeRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of(income));

            List<IncomeResponse> result = incomeService.getAllIncomes(null, JAN_1, JAN_31);

            assertThat(result).extracting(IncomeResponse::id).containsExactly(income.getId());

            ArgumentCaptor<Sort> sort = ArgumentCaptor.forClass(Sort.class);
            verify(incomeRepository).findAll(any(Specification.class), sort.capture());
            assertThat(sort.getValue()).isEqualTo(Sort.by(Sort.Direction.DESC, "date"));
        }
    }

    @Nested
    class GetIncomeById {

        @Test
        void returnsNotFoundWhenMissing() {
            UUID id = UUID.randomUUID();
            when(incomeRepository.findById(id)).thenReturn(Optional.empty());

            assertApiException(() -> incomeService.getIncomeById(id), HttpStatus.NOT_FOUND);
        }

        @Test
        void mapsSourceDonationId() {
            Income income = income(category(CategoryType.INCOME, true));
            Donation donation = new Donation();
            donation.setId(UUID.randomUUID());
            income.setSourceDonation(donation);
            when(incomeRepository.findById(income.getId())).thenReturn(Optional.of(income));

            IncomeResponse response = incomeService.getIncomeById(income.getId());

            assertThat(response.sourceDonationId()).isEqualTo(donation.getId());
        }

        @Test
        void returnsNullSourceDonationIdForManualIncome() {
            Income income = income(category(CategoryType.INCOME, true));
            when(incomeRepository.findById(income.getId())).thenReturn(Optional.of(income));

            IncomeResponse response = incomeService.getIncomeById(income.getId());

            assertThat(response.sourceDonationId()).isNull();
        }
    }

    @Nested
    class CreateIncome {

        @Test
        void savesIncomeWithNormalizedText() {
            Category category = category(CategoryType.INCOME, true);
            when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));
            when(incomeRepository.save(any(Income.class))).thenAnswer(invocation -> invocation.getArgument(0));

            IncomeRequest request = new IncomeRequest(category.getId(), new BigDecimal("50.00"),
                    "  Ofrenda dominical  ", JAN_1);

            IncomeResponse response = incomeService.createIncome(request);

            assertThat(response.categoryId()).isEqualTo(category.getId());
            assertThat(response.amount()).isEqualByComparingTo("50.00");
            assertThat(response.description()).isEqualTo("Ofrenda dominical");
            assertThat(response.sourceDonationId()).isNull();
            assertThat(response.voided()).isFalse();
        }

        @Test
        void storesBlankDescriptionAsNull() {
            Category category = category(CategoryType.INCOME, true);
            when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));
            when(incomeRepository.save(any(Income.class))).thenAnswer(invocation -> invocation.getArgument(0));

            IncomeRequest request = new IncomeRequest(category.getId(), new BigDecimal("50.00"), "   ", JAN_1);

            assertThat(incomeService.createIncome(request).description()).isNull();
        }

        @Test
        void rejectsUnknownCategory() {
            UUID categoryId = UUID.randomUUID();
            when(categoryRepository.findById(categoryId)).thenReturn(Optional.empty());

            assertApiException(() -> incomeService.createIncome(request(categoryId)), HttpStatus.NOT_FOUND);
            verify(incomeRepository, never()).save(any());
        }

        @Test
        void rejectsExpenseCategory() {
            Category category = category(CategoryType.EXPENSE, true);
            when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));

            assertApiException(() -> incomeService.createIncome(request(category.getId())), HttpStatus.BAD_REQUEST);
            verify(incomeRepository, never()).save(any());
        }

        @Test
        void rejectsInactiveCategory() {
            Category category = category(CategoryType.INCOME, false);
            when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));

            assertApiException(() -> incomeService.createIncome(request(category.getId())), HttpStatus.BAD_REQUEST);
            verify(incomeRepository, never()).save(any());
        }
    }

    @Nested
    class UpdateIncome {

        @Test
        void keepsCurrentCategoryEvenIfInactive() {
            Category inactive = category(CategoryType.INCOME, false);
            Income income = income(inactive);
            when(incomeRepository.findById(income.getId())).thenReturn(Optional.of(income));

            IncomeRequest request = new IncomeRequest(inactive.getId(), new BigDecimal("55.00"), null, JAN_31);

            IncomeResponse response = incomeService.updateIncome(income.getId(), request);

            assertThat(response.amount()).isEqualByComparingTo("55.00");
            assertThat(response.date()).isEqualTo(JAN_31);
            assertThat(income.getCategory()).isSameAs(inactive);
            verifyNoInteractions(categoryRepository);
        }

        @Test
        void changesToAnotherActiveCategory() {
            Income income = income(category(CategoryType.INCOME, true));
            Category other = category(CategoryType.INCOME, true);
            when(incomeRepository.findById(income.getId())).thenReturn(Optional.of(income));
            when(categoryRepository.findById(other.getId())).thenReturn(Optional.of(other));

            IncomeResponse response = incomeService.updateIncome(income.getId(), request(other.getId()));

            assertThat(response.categoryId()).isEqualTo(other.getId());
        }

        @Test
        void rejectsChangeToInactiveCategory() {
            Category original = category(CategoryType.INCOME, true);
            Income income = income(original);
            Category inactive = category(CategoryType.INCOME, false);
            when(incomeRepository.findById(income.getId())).thenReturn(Optional.of(income));
            when(categoryRepository.findById(inactive.getId())).thenReturn(Optional.of(inactive));

            assertApiException(() -> incomeService.updateIncome(income.getId(), request(inactive.getId())),
                    HttpStatus.BAD_REQUEST);
            assertThat(income.getCategory()).isSameAs(original);
        }

        @Test
        void rejectsChangeToExpenseCategory() {
            Category original = category(CategoryType.INCOME, true);
            Income income = income(original);
            Category expenseCategory = category(CategoryType.EXPENSE, true);
            when(incomeRepository.findById(income.getId())).thenReturn(Optional.of(income));
            when(categoryRepository.findById(expenseCategory.getId())).thenReturn(Optional.of(expenseCategory));

            assertApiException(() -> incomeService.updateIncome(income.getId(), request(expenseCategory.getId())),
                    HttpStatus.BAD_REQUEST);
            assertThat(income.getCategory()).isSameAs(original);
        }

        @Test
        void rejectsVoidedIncome() {
            Income income = income(category(CategoryType.INCOME, true));
            income.setVoided(true);
            when(incomeRepository.findById(income.getId())).thenReturn(Optional.of(income));

            assertApiException(() -> incomeService.updateIncome(income.getId(), request(income.getCategory().getId())),
                    HttpStatus.CONFLICT);
        }

        @Test
        void returnsNotFoundWhenMissing() {
            UUID id = UUID.randomUUID();
            when(incomeRepository.findById(id)).thenReturn(Optional.empty());

            assertApiException(() -> incomeService.updateIncome(id, request(UUID.randomUUID())), HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    class VoidIncome {

        @Test
        void voidsIncomeWithTrimmedReason() {
            Income income = income(category(CategoryType.INCOME, true));
            when(incomeRepository.findById(income.getId())).thenReturn(Optional.of(income));

            LocalDateTime before = LocalDateTime.now();
            IncomeResponse response = incomeService.voidIncome(income.getId(), new IncomeVoidRequest("  Registro duplicado  "));

            assertThat(response.voided()).isTrue();
            assertThat(response.voidReason()).isEqualTo("Registro duplicado");
            assertThat(response.voidedAt()).isAfterOrEqualTo(before);
        }

        @Test
        void rejectsAlreadyVoidedIncome() {
            Income income = income(category(CategoryType.INCOME, true));
            income.setVoided(true);
            when(incomeRepository.findById(income.getId())).thenReturn(Optional.of(income));

            assertApiException(() -> incomeService.voidIncome(income.getId(), new IncomeVoidRequest("Otra vez")),
                    HttpStatus.CONFLICT);
        }

        @Test
        void returnsNotFoundWhenMissing() {
            UUID id = UUID.randomUUID();
            when(incomeRepository.findById(id)).thenReturn(Optional.empty());

            assertApiException(() -> incomeService.voidIncome(id, new IncomeVoidRequest("Motivo")), HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    class GetSummary {

        @Test
        void sumsCategoryTotals() {
            when(incomeRepository.sumByCategory(JAN_1, JAN_31)).thenReturn(List.of(
                    new IncomeCategoryTotal(UUID.randomUUID(), "Diezmos", new BigDecimal("1200.00"), 4L),
                    new IncomeCategoryTotal(UUID.randomUUID(), "Ofrendas", new BigDecimal("350.75"), 3L)
            ));

            IncomeSummaryResponse summary = incomeService.getSummary(JAN_1, JAN_31);

            assertThat(summary.total()).isEqualByComparingTo("1550.75");
            assertThat(summary.count()).isEqualTo(7);
            assertThat(summary.byCategory()).hasSize(2);
        }

        @Test
        void returnsZeroWhenNoIncomes() {
            when(incomeRepository.sumByCategory(JAN_1, JAN_31)).thenReturn(List.of());

            IncomeSummaryResponse summary = incomeService.getSummary(JAN_1, JAN_31);

            assertThat(summary.total()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(summary.count()).isZero();
            assertThat(summary.byCategory()).isEmpty();
        }

        @Test
        void rejectsFromAfterTo() {
            assertApiException(() -> incomeService.getSummary(JAN_31, JAN_1), HttpStatus.BAD_REQUEST);
            verifyNoInteractions(incomeRepository);
        }
    }

    private static void assertApiException(ThrowingCallable call, HttpStatus status) {
        assertThatThrownBy(call)
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(status);
    }

    private static Category category(CategoryType type, boolean active) {
        return new Category(UUID.randomUUID(), "Category", type, active);
    }

    private static Income income(Category category) {
        Income income = new Income();
        income.setId(UUID.randomUUID());
        income.setCategory(category);
        income.setAmount(new BigDecimal("50.00"));
        income.setDate(JAN_1);
        return income;
    }

    private static IncomeRequest request(UUID categoryId) {
        return new IncomeRequest(categoryId, new BigDecimal("50.00"), null, JAN_1);
    }
}
