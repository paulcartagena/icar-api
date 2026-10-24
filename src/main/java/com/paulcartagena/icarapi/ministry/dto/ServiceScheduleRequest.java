package com.paulcartagena.icarapi.ministry.dto;

import jakarta.validation.constraints.NotNull;

import java.time.DayOfWeek;
import java.time.LocalTime;

public record ServiceScheduleRequest(
        @NotNull(message = "Day of week is required.")
        DayOfWeek dayOfWeek,

        @NotNull(message = "Start time is required.")
        LocalTime startTime
) {
}
