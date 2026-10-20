package com.careflow.scheduling.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public record CreateLeaveRequest(
        @NotNull(message = "Start date time is required")
        LocalDateTime startDateTime,

        @NotNull(message = "End date time is required")
        LocalDateTime endDateTime,

        String reason
) {}
