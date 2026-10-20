package com.careflow.appointment.dto;

import com.careflow.appointment.entity.AppointmentStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateAppointmentStatusRequest(
        @NotNull(message = "New appointment status is required")
        AppointmentStatus status
) {}
