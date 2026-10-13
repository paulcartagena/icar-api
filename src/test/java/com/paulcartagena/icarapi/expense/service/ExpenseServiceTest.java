package com.paulcartagena.icarapi.expense.service;

import com.paulcartagena.icarapi.category.entity.Category;
import com.paulcartagena.icarapi.category.enums.CategoryType;
import com.paulcartagena.icarapi.category.repository.CategoryRepository;
import com.paulcartagena.icarapi.exception.ApiException;
import com.paulcartagena.icarapi.expense.dto.ExpenseCategoryTotal;
import com.paulcartagena.icarapi.expense.dto.ExpenseRequest;
import com.paulcartagena.icarapi.expense.dto.ExpenseResponse;
import com.paulcartagena.icarapi.expense.dto.ExpenseSummaryResponse;
import com.paulcartagena.icarapi.expense.dto.ExpenseVoidRequest;
import com.paulcartagena.icarapi.expense.entity.Expense;
import com.paulcartagena.icarapi.expense.repository.ExpenseRepository;
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
class ExpenseServiceTest {

    private static final LocalDate JAN_1 = LocalDate.of(2026, 1, 1);
    private static final LocalDate JAN_31 = LocalDate.of(2026, 1, 31);

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private ExpenseService expenseService;

    @Nested
    class GetAllExpenses {

        @Test
        void rejectsOnlyOneDateBound() {
            assertApiException(() -> expenseService.getAllExpenses(null, JAN_1, null), HttpStatus.BAD_REQUEST);
            assertApiException(() -> expenseService.getAllExpenses(null, null, JAN_31), HttpStatus.BAD_REQUEST);
            verifyNoInteractions(expenseRepository);
        }

        @Test
        void rejectsFromAfterTo() {
            assertApiException(() -> expenseService.getAllExpenses(null, JAN_31, JAN_1), HttpStatus.BAD_REQUEST);
            verifyNoInteractions(expenseRepository);
        }

        @Test
        @SuppressWarnings("unchecked")
        void returnsExpensesSortedByDateDescending() {
            Expense expense = expense(category(CategoryType.EXPENSE, true));
            when(expenseRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of(expense));

            List<ExpenseResponse> result = expenseService.getAllExpenses(null, JAN_1, JAN_31);

            assertThat(result).extracting(ExpenseResponse::id).containsExactly(expense.getId());

            ArgumentCaptor<Sort> sort = ArgumentCaptor.forClass(Sort.class);
            verify(expenseRepository).findAll(any(Specification.class), sort.capture());
            assertThat(sort.getValue()).isEqualTo(Sort.by(Sort.Direction.DESC, "date"));
        }
    }

    @Nested
    class GetExpenseById {

        @Test
        void returnsNotFoundWhenMissing() {
            UUID id = UUID.randomUUID();
            when(expenseRepository.findById(id)).thenReturn(Optional.empty());

            assertApiException(() -> expenseService.getExpenseById(id), HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    class CreateExpense {

        @Test
        void savesExpenseWithNormalizedText() {
            Category category = category(CategoryType.EXPENSE, true);
            when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));
            when(expenseRepository.save(any(Expense.class))).thenAnswer(invocation -> invocation.getArgument(0));

            ExpenseRequest request = new ExpenseRequest(category.getId(), new BigDecimal("50.00"),
                    "  Recibo de luz  ", JAN_1, "   ");

            ExpenseResponse response = expenseService.createExpense(request);

            assertThat(response.categoryId()).isEqualTo(category.getId());
            assertThat(response.amount()).isEqualByComparingTo("50.00");
            assertThat(response.description()).isEqualTo("Recibo de luz");
            assertThat(response.receiptUrl()).isNull();
            assertThat(response.voided()).isFalse();
        }

        @Test
        void rejectsUnknownCategory() {
            UUID categoryId = UUID.randomUUID();
            when(categoryRepository.findById(categoryId)).thenReturn(Optional.empty());

            assertApiException(() -> expenseService.createExpense(request(categoryId)), HttpStatus.NOT_FOUND);
            verify(expenseRepository, never()).save(any());
        }

        @Test
        void rejectsIncomeCategory() {
            Category category = category(CategoryType.INCOME, true);
            when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));

            assertApiException(() -> expenseService.createExpense(request(category.getId())), HttpStatus.BAD_REQUEST);
            verify(expenseRepository, never()).save(any());
        }

        @Test
        void rejectsInactiveCategory() {
            Category category = category(CategoryType.EXPENSE, false);
            when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));

            assertApiException(() -> expenseService.createExpense(request(category.getId())), HttpStatus.BAD_REQUEST);
            verify(expenseRepository, never()).save(any());
        }
    }

    @Nested
    class UpdateExpense {

        @Test
        void keepsCurrentCategoryEvenIfInactive() {
            Category inactive = category(CategoryType.EXPENSE, false);
            Expense expense = expense(inactive);
            when(expenseRepository.findById(expense.getId())).thenReturn(Optional.of(expense));

            ExpenseRequest request = new ExpenseRequest(inactive.getId(), new BigDecimal("55.00"), null, JAN_31, null);

            ExpenseResponse response = expenseService.updateExpense(expense.getId(), request);

            assertThat(response.amount()).isEqualByComparingTo("55.00");
            assertThat(response.date()).isEqualTo(JAN_31);
            assertThat(expense.getCategory()).isSameAs(inactive);
            verifyNoInteractions(categoryRepository);
        }

        @Test
        void changesToAnotherActiveCategory() {
            Expense expense = expense(category(CategoryType.EXPENSE, true));
            Category other = category(CategoryType.EXPENSE, true);
            when(expenseRepository.findById(expense.getId())).thenReturn(Optional.of(expense));
            when(categoryRepository.findById(other.getId())).thenReturn(Optional.of(other));

            ExpenseResponse response = expenseService.updateExpense(expense.getId(), request(other.getId()));

            assertThat(response.categoryId()).isEqualTo(other.getId());
        }

        @Test
        void rejectsChangeToInactiveCategory() {
            Category original = category(CategoryType.EXPENSE, true);
            Expense expense = expense(original);
            Category inactive = category(CategoryType.EXPENSE, false);
            when(expenseRepository.findById(expense.getId())).thenReturn(Optional.of(expense));
            when(categoryRepository.findById(inactive.getId())).thenReturn(Optional.of(inactive));

            assertApiException(() -> expenseService.updateExpense(expense.getId(), request(inactive.getId())),
                    HttpStatus.BAD_REQUEST);
            assertThat(expense.getCategory()).isSameAs(original);
        }

        @Test
        void rejectsVoidedExpense() {
            Expense expense = expense(category(CategoryType.EXPENSE, true));
            expense.setVoided(true);
            when(expenseRepository.findById(expense.getId())).thenReturn(Optional.of(expense));

            assertApiException(() -> expenseService.updateExpense(expense.getId(), request(expense.getCategory().getId())),
                    HttpStatus.CONFLICT);
        }

        @Test
        void returnsNotFoundWhenMissing() {
            UUID id = UUID.randomUUID();
            when(expenseRepository.findById(id)).thenReturn(Optional.empty());

            assertApiException(() -> expenseService.updateExpense(id, request(UUID.randomUUID())), HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    class VoidExpense {

        @Test
        void voidsExpenseWithTrimmedReason() {
            Expense expense = expense(category(CategoryType.EXPENSE, true));
            when(expenseRepository.findById(expense.getId())).thenReturn(Optional.of(expense));

            LocalDateTime before = LocalDateTime.now();
            ExpenseResponse response = expenseService.voidExpense(expense.getId(), new ExpenseVoidRequest("  Factura duplicada  "));

            assertThat(response.voided()).isTrue();
            assertThat(response.voidReason()).isEqualTo("Factura duplicada");
            assertThat(response.voidedAt()).isAfterOrEqualTo(before);
        }

        @Test
        void rejectsAlreadyVoidedExpense() {
            Expense expense = expense(category(CategoryType.EXPENSE, true));
            expense.setVoided(true);
            when(expenseRepository.findById(expense.getId())).thenReturn(Optional.of(expense));

            assertApiException(() -> expenseService.voidExpense(expense.getId(), new ExpenseVoidRequest("Otra vez")),
                    HttpStatus.CONFLICT);
        }
    }

    @Nested
    class GetSummary {

        @Test
        void sumsCategoryTotals() {
            when(expenseRepository.sumByCategory(JAN_1, JAN_31)).thenReturn(List.of(
                    new ExpenseCategoryTotal(UUID.randomUUID(), "Agua", new BigDecimal("600.00"), 3L),
                    new ExpenseCategoryTotal(UUID.randomUUID(), "Luz", new BigDecimal("450.50"), 2L)
            ));

            ExpenseSummaryResponse summary = expenseService.getSummary(JAN_1, JAN_31);

            assertThat(summary.total()).isEqualByComparingTo("1050.50");
            assertThat(summary.count()).isEqualTo(5);
            assertThat(summary.byCategory()).hasSize(2);
        }

        @Test
        void returnsZeroWhenNoExpenses() {
            when(expenseRepository.sumByCategory(JAN_1, JAN_31)).thenReturn(List.of());

            ExpenseSummaryResponse summary = expenseService.getSummary(JAN_1, JAN_31);

            assertThat(summary.total()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(summary.count()).isZero();
            assertThat(summary.byCategory()).isEmpty();
        }

        @Test
        void rejectsFromAfterTo() {
            assertApiException(() -> expenseService.getSummary(JAN_31, JAN_1), HttpStatus.BAD_REQUEST);
            verifyNoInteractions(expenseRepository);
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

    private static Expense expense(Category category) {
        Expense expense = new Expense();
        expense.setId(UUID.randomUUID());
        expense.setCategory(category);
        expense.setAmount(new BigDecimal("50.00"));
        expense.setDate(JAN_1);
        return expense;
    }

    private static ExpenseRequest request(UUID categoryId) {
        return new ExpenseRequest(categoryId, new BigDecimal("50.00"), null, JAN_1, null);
    }
}
