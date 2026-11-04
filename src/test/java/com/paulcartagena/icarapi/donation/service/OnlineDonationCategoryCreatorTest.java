package com.paulcartagena.icarapi.donation.service;

import com.paulcartagena.icarapi.category.entity.Category;
import com.paulcartagena.icarapi.category.enums.CategoryType;
import com.paulcartagena.icarapi.category.repository.CategoryRepository;
import com.paulcartagena.icarapi.config.PayPalProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OnlineDonationCategoryCreatorTest {

    @Mock
    private CategoryRepository categoryRepository;

    private static PayPalProperties properties(String clientId) {
        return new PayPalProperties(clientId, clientId, "", "https://api-m.sandbox.paypal.com", BigDecimal.ONE,
                BigDecimal.TEN, List.of(), new PayPalProperties.OrderRateLimit(10, Duration.ofHours(1)));
    }

    @Test
    void createsTheCategoryOnceOnlineDonationsAreConfigured() {
        when(categoryRepository.existsByNameIgnoreCaseAndType("Donaciones online", CategoryType.INCOME))
                .thenReturn(false);

        new OnlineDonationCategoryCreator(categoryRepository, properties("client")).run();

        ArgumentCaptor<Category> saved = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepository).save(saved.capture());
        assertThat(saved.getValue().getName()).isEqualTo("Donaciones online");
        assertThat(saved.getValue().getType()).isEqualTo(CategoryType.INCOME);
        assertThat(saved.getValue().isActive()).isTrue();
    }

    @Test
    void doesNothingIfItAlreadyExists() {
        when(categoryRepository.existsByNameIgnoreCaseAndType("Donaciones online", CategoryType.INCOME))
                .thenReturn(true);

        new OnlineDonationCategoryCreator(categoryRepository, properties("client")).run();

        verify(categoryRepository, never()).save(any());
    }

    @Test
    void doesNothingWhilePayPalIsNotConfigured() {
        new OnlineDonationCategoryCreator(categoryRepository, properties("")).run();

        verifyNoInteractions(categoryRepository);
    }
}
