package com.paulcartagena.icarapi.event.controller;

import com.paulcartagena.icarapi.event.dto.EventPhotoRequest;
import com.paulcartagena.icarapi.event.dto.EventPhotoResponse;
import com.paulcartagena.icarapi.event.dto.EventRequest;
import com.paulcartagena.icarapi.event.dto.EventResponse;
import com.paulcartagena.icarapi.event.service.EventService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

// Admin side of the events: lists inactive ones too. There is no CONTENT_READ, so reads also need CONTENT_WRITE
@RestController
@RequestMapping("/events")
@Tag(name = "Events")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @PreAuthorize("hasAuthority('CONTENT_WRITE')")
    @GetMapping
    public List<EventResponse> getAllEvents(@RequestParam(required = false) Boolean active,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return eventService.getAllEvents(active, from, to);
    }

    @PreAuthorize("hasAuthority('CONTENT_WRITE')")
    @GetMapping("/{id}")
    public EventResponse getEventById(@PathVariable UUID id) {
        return eventService.getEventById(id);
    }

    @PreAuthorize("hasAuthority('CONTENT_WRITE')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EventResponse createEvent(@Valid @RequestBody EventRequest eventRequest) {
        return eventService.createEvent(eventRequest);
    }

    @PreAuthorize("hasAuthority('CONTENT_WRITE')")
    @PutMapping("/{id}")
    public EventResponse updateEvent(@PathVariable UUID id, @Valid @RequestBody EventRequest eventRequest) {
        return eventService.updateEvent(id, eventRequest);
    }

    @PreAuthorize("hasAuthority('CONTENT_WRITE')")
    @PatchMapping("/{id}/activate")
    public EventResponse activateEvent(@PathVariable UUID id) {
        return eventService.activateEvent(id);
    }

    @PreAuthorize("hasAuthority('CONTENT_WRITE')")
    @PatchMapping("/{id}/deactivate")
    public EventResponse deactivateEvent(@PathVariable UUID id) {
        return eventService.deactivateEvent(id);
    }

    @PreAuthorize("hasAuthority('CONTENT_WRITE')")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteEvent(@PathVariable UUID id) {
        eventService.deleteEvent(id);
    }

    @PreAuthorize("hasAuthority('CONTENT_WRITE')")
    @PostMapping("/{id}/photos")
    @ResponseStatus(HttpStatus.CREATED)
    public EventPhotoResponse addPhoto(@PathVariable UUID id, @Valid @RequestBody EventPhotoRequest request) {
        return eventService.addPhoto(id, request);
    }

    @PreAuthorize("hasAuthority('CONTENT_WRITE')")
    @PutMapping("/{id}/photos/{photoId}")
    public EventPhotoResponse updatePhoto(@PathVariable UUID id, @PathVariable UUID photoId,
                                          @Valid @RequestBody EventPhotoRequest request) {
        return eventService.updatePhoto(id, photoId, request);
    }

    @PreAuthorize("hasAuthority('CONTENT_WRITE')")
    @DeleteMapping("/{id}/photos/{photoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePhoto(@PathVariable UUID id, @PathVariable UUID photoId) {
        eventService.deletePhoto(id, photoId);
    }
}
