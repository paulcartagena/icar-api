package com.paulcartagena.icarapi.event.repository;

import com.paulcartagena.icarapi.event.entity.Event;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventRepository extends JpaRepository<Event, UUID> {

    Optional<Event> findByIdAndActiveTrue(UUID id);
    List<Event> findByActiveTrueAndStartAtAfter(LocalDateTime startAt, Sort sort);
}
