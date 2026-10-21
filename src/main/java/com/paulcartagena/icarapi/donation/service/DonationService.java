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
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.paulcartagena.icarapi.donation.repository.DonationSpecifications.completedBetween;
import static com.paulcartagena.icarapi.donation.repository.DonationSpecifications.hasMethod;
import static com.paulcartagena.icarapi.donation.repository.DonationSpecifications.hasStatus;

@Service
@Transactional
public class DonationService {

    static final String ANONYMOUS_DONOR = "Anónimo";

    private final DonationRepository donationRepository;
    private final IncomeRepository incomeRepository;
    private final CategoryRepository categoryRepository;
    private final AppUserRepository appUserRepository;

    public DonationService(DonationRepository donationRepository,
                           IncomeRepository incomeRepository,
                           CategoryRepository categoryRepository,
                           AppUserRepository appUserRepository) {
        this.donationRepository = donationRepository;
        this.incomeRepository = incomeRepository;
        this.categoryRepository = categoryRepository;
        this.appUserRepository = appUserRepository;
    }

    @Transactional(readOnly = true)
    public List<DonationResponse> getAllDonations(DonationStatus status, DonationMethod method,
                                                  LocalDate from, LocalDate to) {
        // Pending donations (no completedAt) first, then the most recent
        Sort sort = Sort.by(Sort.Order.desc("completedAt").nullsFirst(), Sort.Order.desc("createdAt"));

        // Both bounds or neither
        if ((from == null) != (to == null)) {
            throw ApiException.badRequest("Both 'from' and 'to' are required to filter.");
        }
        if ((from != null) && from.isAfter(to)) {
            throw ApiException.badRequest("'from' must be before or equal to 'to'.");
        }

        Specification<Donation> spec = Specification.unrestricted();
        if (status != null) {
            spec = spec.and(hasStatus(status));
        }
        if (method != null) {
            spec = spec.and(hasMethod(method));
        }
        if (from != null) {
            spec = spec.and(completedBetween(from, to));
        }

        List<Donation> donations = donationRepository.findAll(spec, sort);
        if (donations.isEmpty()) {
            return List.of();
        }

        // One query for all the incomes instead of one per donation
        List<UUID> donationIds = donations.stream().map(Donation::getId).toList();
        Map<UUID, Income> incomeByDonationId = incomeRepository.findBySourceDonationIdIn(donationIds).stream()
                .collect(Collectors.toMap(income -> income.getSourceDonation().getId(), Function.identity()));

        return donations.stream()
                .map(donation -> buildResponse(donation, incomeByDonationId.get(donation.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public DonationResponse getDonationById(UUID id) {
        Donation donation = findDonation(id);
        return buildResponse(donation, incomeRepository.findBySourceDonationId(id).orElse(null));
    }

    // Registers a donation received in person and its income, in the same transaction
    public DonationResponse createDonation(DonationRequest donationRequest, UUID registeredById) {
        // PayPal donations will come from the PayPal checkout flow, never typed in by hand
        if (donationRequest.method() == DonationMethod.PAYPAL) {
            throw ApiException.badRequest("PayPal donations cannot be registered manually.");
        }

        Category category = findValidIncomeCategory(donationRequest.categoryId());

        // The id comes from our own signed JWT, so a reference (no SELECT) is enough to set the FK
        AppUser registeredBy = appUserRepository.getReferenceById(registeredById);

        Donation donation = new Donation();
        donation.setDonorName(donorNameOrAnonymous(donationRequest.donorName()));
        donation.setMessage(normalize(donationRequest.message()));
        donation.setAmount(donationRequest.amount());
        donation.setMethod(donationRequest.method());
        // Received in person, so it is already completed. Only the day is known, not the time.
        donation.setStatus(DonationStatus.COMPLETED);
        donation.setCompletedAt(donationRequest.date().atStartOfDay());
        donation.setRegisteredBy(registeredBy);
        Donation savedDonation = donationRepository.save(donation);

        Income income = new Income();
        income.setCategory(category);
        income.setSourceDonation(savedDonation);
        income.setAmount(savedDonation.getAmount());
        income.setDescription("Donación - " + savedDonation.getDonorName());
        income.setDate(donationRequest.date());
        income.setRegisteredBy(registeredBy);
        Income savedIncome = incomeRepository.save(income);

        return buildResponse(savedDonation, savedIncome);
    }

    // Voids the donation and its income together, so accounting never counts a voided donation
    public DonationResponse voidDonation(UUID id, DonationVoidRequest donationVoidRequest) {
        Donation donation = findDonation(id);

        // Voiding is final: it cannot be repeated or reverted
        if (donation.isVoided()) {
            throw ApiException.conflict("Donation is already voided.");
        }

        LocalDateTime now = LocalDateTime.now();
        String reason = donationVoidRequest.reason().trim();

        donation.setVoided(true);
        donation.setVoidedAt(now);
        donation.setVoidReason(reason);

        Income income = incomeRepository.findBySourceDonationId(id).orElse(null);
        if (income != null && !income.isVoided()) {
            income.setVoided(true);
            income.setVoidedAt(now);
            income.setVoidReason(reason);
        }

        return buildResponse(donation, income);
    }

    private Donation findDonation(UUID id) {
        return donationRepository.findById(id)
                .orElseThrow(() -> ApiException.resourceNotFound("Donation not found: " + id));
    }

    // Validation: should be income category
    private Category findValidIncomeCategory(UUID categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> ApiException.resourceNotFound("Category not found: " + categoryId));

        if (category.getType() != CategoryType.INCOME) {
            throw ApiException.badRequest("Category must be of type INCOME.");
        }

        if (!category.isActive()) {
            throw ApiException.badRequest("Category is inactive.");
        }

        return category;
    }

    private String donorNameOrAnonymous(String donorName) {
        String normalized = normalize(donorName);
        return normalized == null ? ANONYMOUS_DONOR : normalized;
    }

    private String normalize(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private DonationResponse buildResponse(Donation donation, Income income) {
        return new DonationResponse(
                donation.getId(),
                donation.getDonorName(),
                donation.getMessage(),
                donation.getAmount(),
                donation.getMethod(),
                donation.getStatus(),
                donation.getCompletedAt(),
                income == null ? null : income.getId(),
                income == null ? null : income.getCategory().getId(),
                income == null ? null : income.getCategory().getName(),
                donation.isVoided(),
                donation.getVoidedAt(),
                donation.getVoidReason(),
                donation.getCreatedAt()
        );
    }
}
