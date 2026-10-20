package com.careflow.doctor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record CreateDoctorRequest(
        @NotNull(message = "User account ID is required")
        Long userId,

        @NotNull(message = "Department ID is required")
        Long departmentId,

        @NotBlank(message = "Specialization is required")
        String specialization,

        @NotNull(message = "Consultation fee is required")
        @Positive(message = "Consultation fee must be positive")
        BigDecimal consultationFee
) {}
