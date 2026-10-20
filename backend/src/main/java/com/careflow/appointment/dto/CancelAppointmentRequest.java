package com.careflow.appointment.dto;

import jakarta.validation.constraints.NotBlank;

public record CancelAppointmentRequest(
        @NotBlank(message = "Cancellation reason is required")
        String cancellationReason
) {}
