package com.careflow.patient.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;

public record UpdatePatientRequest(
        LocalDate dateOfBirth,
        @NotBlank(message = "Contact number is required")
        String contactNumber,
        String address
) {}
