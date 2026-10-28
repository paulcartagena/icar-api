package com.paulcartagena.icarapi.event.repository;

import com.paulcartagena.icarapi.event.entity.Event;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.LocalDateTime;

// Reusable filters for the event lists: each one is combined with .and() only when it applies
public final class EventSpecifications {

    private EventSpecifications() {
    }

    public static Specification<Event> hasActive(boolean active) {
        return (root, query, cb) -> cb.equal(root.get("active"), active);
    }

    // Events that take place (at least partly) between from and to, both days inclusive
    public static Specification<Event> overlaps(LocalDate from, LocalDate to) {
        return (root, query, cb) -> cb.and(
                cb.lessThan(root.get("startAt"), to.plusDays(1).atStartOfDay()),
                cb.greaterThanOrEqualTo(root.get("endAt"), from.atStartOfDay()));
    }

    // Upcoming or still in progress
    public static Specification<Event> endsOnOrAfter(LocalDateTime moment) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("endAt"), moment);
    }
}
