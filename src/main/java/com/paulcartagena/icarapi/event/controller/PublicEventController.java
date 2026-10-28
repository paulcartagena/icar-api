package com.paulcartagena.icarapi.event.controller;

import com.paulcartagena.icarapi.event.dto.EventResponse;
import com.paulcartagena.icarapi.event.service.EventService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

// Read-only events for the public website: only active ones, no token needed (GET /public/** in SecurityConfig)
@RestController
@RequestMapping("/public/events")
@Tag(name = "Public")
public class PublicEventController {

    private final EventService eventService;

    public PublicEventController(EventService eventService) {
        this.eventService = eventService;
    }

    @PreAuthorize("permitAll()")
    @GetMapping
    public List<EventResponse> getEvents(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return eventService.getPublicEvents(from, to);
    }

    @PreAuthorize("permitAll()")
    @GetMapping("/{id}")
    public EventResponse getEventById(@PathVariable UUID id) {
        return eventService.getPublicEventById(id);
    }
}
