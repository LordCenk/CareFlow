package com.careflow.scheduling.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

public record CreateAvailabilityRequest(
        @NotNull(message = "Day of week is required")
        DayOfWeek dayOfWeek,

        @NotNull(message = "Start time is required")
        LocalTime startTime,

        @NotNull(message = "End time is required")
        LocalTime endTime,

        @Positive(message = "Slot duration must be positive")
        Integer slotDurationMinutes,

        LocalDate effectiveFrom,

        LocalDate effectiveUntil
) {}
