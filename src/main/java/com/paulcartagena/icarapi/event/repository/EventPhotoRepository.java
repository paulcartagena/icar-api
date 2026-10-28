package com.paulcartagena.icarapi.event.repository;

import com.paulcartagena.icarapi.event.entity.EventPhoto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EventPhotoRepository extends JpaRepository<EventPhoto, UUID> {

    Optional<EventPhoto> findByIdAndEventId(UUID id, UUID eventId);
}
