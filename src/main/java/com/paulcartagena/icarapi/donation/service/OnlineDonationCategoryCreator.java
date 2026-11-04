package com.paulcartagena.icarapi.donation.service;

import com.paulcartagena.icarapi.category.enums.CategoryType;
import com.paulcartagena.icarapi.category.repository.CategoryRepository;
import com.paulcartagena.icarapi.config.PayPalProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import static com.paulcartagena.icarapi.donation.service.OnlineDonationService.CATEGORY_NAME;

// Online donations always go to the "Donaciones online" INCOME category: it is created at startup once PayPal is
// configured, so the panel shows it (and budgets can use it) before the first donation arrives
@Slf4j
@Component
public class OnlineDonationCategoryCreator implements CommandLineRunner {

    private final CategoryRepository categoryRepository;
    private final PayPalProperties properties;

    public OnlineDonationCategoryCreator(CategoryRepository categoryRepository, PayPalProperties properties) {
        this.categoryRepository = categoryRepository;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (!properties.configured() || categoryRepository.existsByNameIgnoreCaseAndType(CATEGORY_NAME, CategoryType.INCOME)) {
            return;
        }
        categoryRepository.save(OnlineDonationService.newOnlineDonationsCategory());
        log.info("Created the '{}' income category for online donations.", CATEGORY_NAME);
    }
}
