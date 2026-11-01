package com.paulcartagena.icarapi.ministry.dto;

import java.util.List;
import java.util.UUID;

// What the public website sees of a ministry: nothing about its members, not even how many there are
public record PublicMinistryResponse(
        UUID id,
        String name,
        String description,
        List<ServiceScheduleResponse> schedules
) {
}
