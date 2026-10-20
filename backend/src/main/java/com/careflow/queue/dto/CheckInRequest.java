package com.careflow.queue.dto;

import jakarta.validation.constraints.NotNull;

public record CheckInRequest(
        @NotNull(message = "Appointment ID is required for patient check-in")
        Long appointmentId
) {}
