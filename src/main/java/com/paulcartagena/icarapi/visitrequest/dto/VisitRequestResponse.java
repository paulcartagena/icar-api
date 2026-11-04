package com.paulcartagena.icarapi.visitrequest.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record VisitRequestResponse(
        UUID id,
        String fullName,
        String phone,
        String email,
        String message,
        LocalDateTime createdAt
) {
}
