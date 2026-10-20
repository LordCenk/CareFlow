package com.careflow.scheduling.dto;

import java.time.LocalDateTime;

public record AvailableSlotResponse(
        LocalDateTime startDateTime,
        LocalDateTime endDateTime,
        int durationMinutes,
        boolean available
) {}
