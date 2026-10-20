package com.paulcartagena.icarapi.budget.service;

import com.paulcartagena.icarapi.budget.dto.BudgetRequest;
import com.paulcartagena.icarapi.budget.dto.BudgetResponse;
import com.paulcartagena.icarapi.budget.entity.Budget;
import com.paulcartagena.icarapi.budget.repository.BudgetRepository;
import com.paulcartagena.icarapi.category.entity.Category;
import com.paulcartagena.icarapi.category.enums.CategoryType;
import com.paulcartagena.icarapi.category.repository.CategoryRepository;
import com.paulcartagena.icarapi.exception.ApiException;
import com.paulcartagena.icarapi.expense.repository.ExpenseRepository;
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
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
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
class BudgetServiceTest {

    private static final LocalDate JAN_1 = LocalDate.of(2026, 1, 1);
    private static final LocalDate JAN_31 = LocalDate.of(2026, 1, 31);

    @Mock
    private BudgetRepository budgetRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private IncomeRepository incomeRepository;

    @Mock
    private AppUserRepository appUserRepository;

    @InjectMocks
    private BudgetService budgetService;

    @Nested
    class GetAllBudgets {

        @Test
        void rejectsOnlyOneDateBound() {
            assertApiException(() -> budgetService.getAllBudgets(null, JAN_1, null), HttpStatus.BAD_REQUEST);
            assertApiException(() -> budgetService.getAllBudgets(null, null, JAN_31), HttpStatus.BAD_REQUEST);
            verifyNoInteractions(budgetRepository);
        }

        @Test
        void rejectsFromAfterTo() {
            assertApiException(() -> budgetService.getAllBudgets(null, JAN_31, JAN_1), HttpStatus.BAD_REQUEST);
            verifyNoInteractions(budgetRepository);
        }
    }

    @Nested
    class GetBudgetById {

        @Test
        void comparesPlannedAgainstExpenses() {
            Budget budget = budget(category(CategoryType.EXPENSE, true));
            when(budgetRepository.findById(budget.getId())).thenReturn(Optional.of(budget));
            when(expenseRepository.sumByCategoryBetween(budget.getCategory().getId(), JAN_1, JAN_31))
                    .thenReturn(new BigDecimal("650.00"));

            BudgetResponse response = budgetService.getBudgetById(budget.getId());

            assertThat(response.actualAmount()).isEqualByComparingTo("650.00");
            assertThat(response.remainingAmount()).isEqualByComparingTo("-150.00");
            verifyNoInteractions(incomeRepository);
        }

        @Test
        void comparesPlannedAgainstIncomes() {
            Budget budget = budget(category(CategoryType.INCOME, true));
            when(budgetRepository.findById(budget.getId())).thenReturn(Optional.of(budget));
            when(incomeRepository.sumByCategoryBetween(budget.getCategory().getId(), JAN_1, JAN_31))
                    .thenReturn(new BigDecimal("200.00"));

            BudgetResponse response = budgetService.getBudgetById(budget.getId());

            assertThat(response.actualAmount()).isEqualByComparingTo("200.00");
            assertThat(response.remainingAmount()).isEqualByComparingTo("300.00");
            verifyNoInteractions(expenseRepository);
        }

        @Test
        void returnsNotFoundWhenMissing() {
            UUID id = UUID.randomUUID();
            when(budgetRepository.findById(id)).thenReturn(Optional.empty());

            assertApiException(() -> budgetService.getBudgetById(id), HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    class CreateBudget {

        @Test
        void savesBudgetWithCreator() {
            Category category = category(CategoryType.EXPENSE, true);
            when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));
            when(budgetRepository.findOverlapping(category.getId(), JAN_1, JAN_31)).thenReturn(List.of());
            when(budgetRepository.save(any(Budget.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(expenseRepository.sumByCategoryBetween(category.getId(), JAN_1, JAN_31)).thenReturn(BigDecimal.ZERO);

            UUID userId = UUID.randomUUID();
            AppUser user = new AppUser();
            when(appUserRepository.getReferenceById(userId)).thenReturn(user);

            BudgetResponse response = budgetService.createBudget(request(category.getId(), JAN_1, JAN_31), userId);

            assertThat(response.plannedAmount()).isEqualByComparingTo("500.00");
            assertThat(response.remainingAmount()).isEqualByComparingTo("500.00");

            ArgumentCaptor<Budget> captor = ArgumentCaptor.forClass(Budget.class);
            verify(budgetRepository).save(captor.capture());
            assertThat(captor.getValue().getCreatedBy()).isSameAs(user);
        }

        @Test
        void rejectsStartAfterEnd() {
            assertApiException(() -> budgetService.createBudget(request(UUID.randomUUID(), JAN_31, JAN_1), UUID.randomUUID()),
                    HttpStatus.BAD_REQUEST);
            verifyNoInteractions(categoryRepository, budgetRepository);
        }

        @Test
        void rejectsUnknownCategory() {
            UUID categoryId = UUID.randomUUID();
            when(categoryRepository.findById(categoryId)).thenReturn(Optional.empty());

            assertApiException(() -> budgetService.createBudget(request(categoryId, JAN_1, JAN_31), UUID.randomUUID()),
                    HttpStatus.NOT_FOUND);
            verify(budgetRepository, never()).save(any());
        }

        @Test
        void rejectsInactiveCategory() {
            Category category = category(CategoryType.EXPENSE, false);
            when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));

            assertApiException(() -> budgetService.createBudget(request(category.getId(), JAN_1, JAN_31), UUID.randomUUID()),
                    HttpStatus.BAD_REQUEST);
            verify(budgetRepository, never()).save(any());
        }

        @Test
        void rejectsOverlappingPeriod() {
            Category category = category(CategoryType.EXPENSE, true);
            when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));
            when(budgetRepository.findOverlapping(category.getId(), JAN_1, JAN_31)).thenReturn(List.of(budget(category)));

            assertApiException(() -> budgetService.createBudget(request(category.getId(), JAN_1, JAN_31), UUID.randomUUID()),
                    HttpStatus.CONFLICT);
            verify(budgetRepository, never()).save(any());
        }
    }

    @Nested
    class UpdateBudget {

        @Test
        void keepsCurrentCategoryEvenIfInactive() {
            Category inactive = category(CategoryType.EXPENSE, false);
            Budget budget = budget(inactive);
            when(budgetRepository.findById(budget.getId())).thenReturn(Optional.of(budget));
            // Its own period overlaps itself, which must not count as a conflict
            when(budgetRepository.findOverlapping(inactive.getId(), JAN_1, JAN_31)).thenReturn(List.of(budget));
            when(expenseRepository.sumByCategoryBetween(inactive.getId(), JAN_1, JAN_31)).thenReturn(BigDecimal.ZERO);

            BudgetResponse response = budgetService.updateBudget(budget.getId(),
                    new BudgetRequest(inactive.getId(), JAN_1, JAN_31, new BigDecimal("800.00")));

            assertThat(response.plannedAmount()).isEqualByComparingTo("800.00");
            assertThat(budget.getCategory()).isSameAs(inactive);
            verifyNoInteractions(categoryRepository);
        }

        @Test
        void rejectsChangeToInactiveCategory() {
            Category original = category(CategoryType.EXPENSE, true);
            Budget budget = budget(original);
            Category inactive = category(CategoryType.EXPENSE, false);
            when(budgetRepository.findById(budget.getId())).thenReturn(Optional.of(budget));
            when(categoryRepository.findById(inactive.getId())).thenReturn(Optional.of(inactive));

            assertApiException(() -> budgetService.updateBudget(budget.getId(), request(inactive.getId(), JAN_1, JAN_31)),
                    HttpStatus.BAD_REQUEST);
            assertThat(budget.getCategory()).isSameAs(original);
        }

        @Test
        void rejectsOverlapWithAnotherBudget() {
            Category category = category(CategoryType.EXPENSE, true);
            Budget budget = budget(category);
            LocalDate feb28 = LocalDate.of(2026, 2, 28);
            when(budgetRepository.findById(budget.getId())).thenReturn(Optional.of(budget));
            when(budgetRepository.findOverlapping(category.getId(), JAN_1, feb28)).thenReturn(List.of(budget, budget(category)));

            assertApiException(() -> budgetService.updateBudget(budget.getId(), request(category.getId(), JAN_1, feb28)),
                    HttpStatus.CONFLICT);
            assertThat(budget.getPeriodEnd()).isEqualTo(JAN_31);
        }
    }

    @Nested
    class DeleteBudget {

        @Test
        void deletesExistingBudget() {
            Budget budget = budget(category(CategoryType.EXPENSE, true));
            when(budgetRepository.findById(budget.getId())).thenReturn(Optional.of(budget));

            budgetService.deleteBudget(budget.getId());

            verify(budgetRepository).delete(budget);
        }

        @Test
        void returnsNotFoundWhenMissing() {
            UUID id = UUID.randomUUID();
            when(budgetRepository.findById(id)).thenReturn(Optional.empty());

            assertApiException(() -> budgetService.deleteBudget(id), HttpStatus.NOT_FOUND);
            verify(budgetRepository, never()).delete(any(Budget.class));
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

    private static Budget budget(Category category) {
        Budget budget = new Budget();
        budget.setId(UUID.randomUUID());
        budget.setCategory(category);
        budget.setPeriodStart(JAN_1);
        budget.setPeriodEnd(JAN_31);
        budget.setPlannedAmount(new BigDecimal("500.00"));
        return budget;
    }

    private static BudgetRequest request(UUID categoryId, LocalDate start, LocalDate end) {
        return new BudgetRequest(categoryId, start, end, new BigDecimal("500.00"));
    }
}
