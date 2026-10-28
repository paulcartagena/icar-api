package com.paulcartagena.icarapi.event.service;

import com.paulcartagena.icarapi.event.dto.*;
import com.paulcartagena.icarapi.event.entity.Event;
import com.paulcartagena.icarapi.event.entity.EventPhoto;
import com.paulcartagena.icarapi.event.repository.EventPhotoRepository;
import com.paulcartagena.icarapi.event.repository.EventRepository;
import com.paulcartagena.icarapi.exception.ApiException;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static com.paulcartagena.icarapi.event.repository.EventSpecifications.*;

@Service
@Transactional
public class EventService {

    private final EventRepository eventRepository;
    private final EventPhotoRepository eventPhotoRepository;

    public EventService(EventRepository eventRepository, EventPhotoRepository eventPhotoRepository) {
        this.eventRepository = eventRepository;
        this.eventPhotoRepository = eventPhotoRepository;
    }

    // --- Admin ---

    @Transactional(readOnly = true)
    public List<EventResponse> getAllEvents(Boolean active, LocalDate from, LocalDate to) {
        validateRange(from, to);

        // Start unrestricted (all events) and add only the filters that were sent
        Specification<Event> spec = Specification.unrestricted();
        if (active != null) {
            spec = spec.and(hasActive(active));
        }
        if (from != null) {
            spec = spec.and(overlaps(from, to));
        }

        return findAll(spec, Sort.Direction.DESC);
    }

    @Transactional(readOnly = true)
    public EventResponse getEventById(UUID id) {
        return buildResponse(findEvent(id));
    }

    public EventResponse createEvent(EventRequest eventRequest) {
        Event event = new Event();
        applyRequest(event, eventRequest);
        event.setActive(true);

        Event savedEvent = eventRepository.save(event);
        return buildResponse(savedEvent);
    }

    public EventResponse updateEvent(UUID id, EventRequest eventRequest) {
        Event event = findEvent(id);
        applyRequest(event, eventRequest);
        return buildResponse(event);
    }

    public EventResponse activateEvent(UUID id) {
        return changeActive(id, true);
    }

    public EventResponse deactivateEvent(UUID id) {
        return changeActive(id, false);
    }

    // Events are site content, not money movements: they can be deleted (photos go with them)
    public void deleteEvent(UUID id) {
        eventRepository.delete(findEvent(id));
    }

    // --- Photos ---

    public EventPhotoResponse addPhoto(UUID eventId, EventPhotoRequest request) {
        Event event = findEvent(eventId);

        EventPhoto photo = new EventPhoto();
        photo.setEvent(event);
        photo.setImageUrl(request.imageUrl().trim());
        photo.setDisplayOrder(request.displayOrder() != null ? request.displayOrder() : nextDisplayOrder(event));
        event.getPhotos().add(photo);

        EventPhoto savedPhoto = eventPhotoRepository.save(photo);
        return buildPhotoResponse(savedPhoto);
    }

    public EventPhotoResponse updatePhoto(UUID eventId, UUID photoId, EventPhotoRequest request) {
        EventPhoto photo = findPhoto(eventId, photoId);

        photo.setImageUrl(request.imageUrl().trim());
        if (request.displayOrder() != null) {
            photo.setDisplayOrder(request.displayOrder());
        }
        return buildPhotoResponse(photo);
    }

    public void deletePhoto(UUID eventId, UUID photoId) {
        eventPhotoRepository.delete(findPhoto(eventId, photoId));
    }

    // --- Public site ---

    // Without a range: upcoming events and those still in progress, soonest first
    @Transactional(readOnly = true)
    public List<EventResponse> getPublicEvents(LocalDate from, LocalDate to) {
        validateRange(from, to);

        Specification<Event> spec = hasActive(true);
        spec = spec.and(from != null ? overlaps(from, to) : endsOnOrAfter(LocalDateTime.now()));

        return findAll(spec, Sort.Direction.ASC);
    }

    // Inactive events are hidden from the site, so they answer 404 just like missing ones
    @Transactional(readOnly = true)
    public EventResponse getPublicEventById(UUID id) {
        return eventRepository.findByIdAndActiveTrue(id)
                .map(this::buildResponse)
                .orElseThrow(() -> ApiException.resourceNotFound("Event not found: " + id));
    }

    // --- Helpers ---

    private List<EventResponse> findAll(Specification<Event> spec, Sort.Direction direction) {
        return eventRepository.findAll(spec, Sort.by(direction, "startAt")).stream()
                .map(this::buildResponse)
                .toList();
    }

    private void applyRequest(Event event, EventRequest eventRequest) {
        if (eventRequest.endAt().isBefore(eventRequest.startAt())) {
            throw ApiException.badRequest("Event can't end before it starts.");
        }

        event.setTitle(eventRequest.title().trim());
        event.setDescription(eventRequest.description().trim());
        event.setStartAt(eventRequest.startAt());
        event.setEndAt(eventRequest.endAt());
        event.setLocation(blankToNull(eventRequest.location()));
    }

    // Idempotent: setting the current value again is a no-op
    private EventResponse changeActive(UUID id, boolean active) {
        Event event = findEvent(id);
        event.setActive(active);
        return buildResponse(event);
    }

    // Both bounds or neither
    private static void validateRange(LocalDate from, LocalDate to) {
        if ((from == null) != (to == null)) {
            throw ApiException.badRequest("Both 'from' and 'to' are required to filter.");
        }
        if ((from != null) && from.isAfter(to)) {
            throw ApiException.badRequest("'from' must be before or equal to 'to'.");
        }
    }

    private static int nextDisplayOrder(Event event) {
        return event.getPhotos().stream()
                .mapToInt(EventPhoto::getDisplayOrder)
                .max()
                .orElse(0) + 1;
    }

    private Event findEvent(UUID id) {
        return eventRepository.findById(id)
                .orElseThrow(() -> ApiException.resourceNotFound("Event not found: " + id));
    }

    private EventPhoto findPhoto(UUID eventId, UUID photoId) {
        findEvent(eventId);
        return eventPhotoRepository.findByIdAndEventId(photoId, eventId)
                .orElseThrow(() -> ApiException.resourceNotFound("Photo not found: " + photoId));
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private EventResponse buildResponse(Event event) {
        List<EventPhotoResponse> photos = event.getPhotos().stream()
                .sorted(Comparator.comparing(EventPhoto::getDisplayOrder))
                .map(this::buildPhotoResponse)
                .toList();

        return new EventResponse(
                event.getId(),
                event.getTitle(),
                event.getDescription(),
                event.getStartAt(),
                event.getEndAt(),
                event.getLocation(),
                event.isActive(),
                photos
        );
    }

    private EventPhotoResponse buildPhotoResponse(EventPhoto photo) {
        return new EventPhotoResponse(photo.getId(), photo.getImageUrl(), photo.getDisplayOrder());
    }
}
