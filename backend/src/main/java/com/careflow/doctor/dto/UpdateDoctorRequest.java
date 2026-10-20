package com.careflow.doctor.dto;

import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record UpdateDoctorRequest(
        Long departmentId,
        String specialization,
        @Positive(message = "Consultation fee must be positive")
        BigDecimal consultationFee,
        Boolean active
) {}
