package com.careflow.appointment.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public record BookAppointmentRequest(
        @NotNull(message = "Patient ID is required")
        Long patientId,

        @NotNull(message = "Doctor ID is required")
        Long doctorId,

        @NotNull(message = "Start date time is required")
        LocalDateTime startDateTime,

        @NotNull(message = "End date time is required")
        LocalDateTime endDateTime,

        String reasonForVisit
) {}
