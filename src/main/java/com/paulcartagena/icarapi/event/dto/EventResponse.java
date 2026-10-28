package com.paulcartagena.icarapi.event.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record EventResponse(
        UUID id,
        String title,
        String description,
        LocalDateTime startAt,
        LocalDateTime endAt,
        String location,
        boolean active,
        List<EventPhotoResponse> photos
) {
}
