package com.paulcartagena.icarapi.ministry.dto;

import java.util.List;
import java.util.UUID;

public record MinistryResponse(
        UUID id,
        String name,
        String description,
        int memberCount,
        List<ServiceScheduleResponse> schedules
) {
}
