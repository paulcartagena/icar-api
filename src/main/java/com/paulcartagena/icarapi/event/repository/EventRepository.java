package com.paulcartagena.icarapi.event.repository;

import com.paulcartagena.icarapi.event.entity.Event;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventRepository extends JpaRepository<Event, UUID>, JpaSpecificationExecutor<Event> {

    Optional<Event> findByIdAndActiveTrue(UUID id);

    // Lists always return the photos, so fetch them in the same query instead of one per event
    @Override
    @EntityGraph(attributePaths = "photos")
    List<Event> findAll(Specification<Event> spec, Sort sort);
}
