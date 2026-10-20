package com.careflow.appointment.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public record RescheduleAppointmentRequest(
        @NotNull(message = "New start date time is required")
        LocalDateTime newStartDateTime,

        @NotNull(message = "New end date time is required")
        LocalDateTime newEndDateTime,

        String reason
) {}
