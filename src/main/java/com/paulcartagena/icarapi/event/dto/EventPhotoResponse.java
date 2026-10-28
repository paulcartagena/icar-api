package com.paulcartagena.icarapi.event.dto;

import java.util.UUID;

public record EventPhotoResponse(
        UUID id,
        String imageUrl,
        int displayOrder
) {
}
