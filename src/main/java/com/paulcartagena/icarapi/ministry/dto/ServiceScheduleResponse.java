package com.paulcartagena.icarapi.ministry.dto;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.UUID;

public record ServiceScheduleResponse(
        UUID id,
        DayOfWeek dayOfWeek,
        LocalTime startTime
) {
}
