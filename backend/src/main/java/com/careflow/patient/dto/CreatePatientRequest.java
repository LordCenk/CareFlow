package com.careflow.patient.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;

public record CreatePatientRequest(
        Long userId,
        String name,
        String email,
        LocalDate dateOfBirth,
        @NotBlank(message = "Contact number is required")
        String contactNumber,
        String address
) {}
