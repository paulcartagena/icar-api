package com.paulcartagena.icarapi.donation.repository;

import com.paulcartagena.icarapi.donation.entity.Donation;
import com.paulcartagena.icarapi.donation.enums.DonationMethod;
import com.paulcartagena.icarapi.donation.enums.DonationStatus;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;

// Reusable filters for GET /donations: each one is combined with .and() only when its param is present
public final class DonationSpecifications {

    private DonationSpecifications() {
    }

    public static Specification<Donation> hasStatus(DonationStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Donation> hasMethod(DonationMethod method) {
        return (root, query, cb) -> cb.equal(root.get("method"), method);
    }

    // completedAt is a timestamp: [from 00:00, the day after 'to' 00:00) includes the whole 'to' day
    public static Specification<Donation> completedBetween(LocalDate from, LocalDate to) {
        return (root, query, cb) -> cb.and(
                cb.greaterThanOrEqualTo(root.get("completedAt"), from.atStartOfDay()),
                cb.lessThan(root.get("completedAt"), to.plusDays(1).atStartOfDay())
        );
    }
}
