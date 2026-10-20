package com.careflow.patient.dto;

import com.careflow.patient.entity.Patient;
import java.time.Instant;
import java.time.LocalDate;

public record PatientResponse(
        Long id,
        Long userId,
        String patientReference,
        String name,
        String email,
        LocalDate dateOfBirth,
        String contactNumber,
        String address,
        Instant createdAt
) {
    public static PatientResponse fromEntity(Patient patient) {
        String name = patient.getUser() != null ? patient.getUser().getName() : "Anonymous Patient";
        String email = patient.getUser() != null ? patient.getUser().getEmail() : null;
        Long userId = patient.getUser() != null ? patient.getUser().getId() : null;

        return new PatientResponse(
                patient.getId(),
                userId,
                patient.getPatientReference(),
                name,
                email,
                patient.getDateOfBirth(),
                patient.getContactNumber(),
                patient.getAddress(),
                patient.getCreatedAt()
        );
    }
}
