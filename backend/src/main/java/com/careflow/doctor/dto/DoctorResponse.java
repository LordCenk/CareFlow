package com.careflow.doctor.dto;

import com.careflow.doctor.entity.Doctor;
import java.math.BigDecimal;
import java.time.Instant;

public record DoctorResponse(
        Long id,
        Long userId,
        String name,
        String email,
        String doctorReference,
        Long departmentId,
        String departmentName,
        String specialization,
        BigDecimal consultationFee,
        boolean active,
        Instant createdAt
) {
    public static DoctorResponse fromEntity(Doctor doctor) {
        String name = doctor.getUser() != null ? doctor.getUser().getName() : "Unknown Doctor";
        String email = doctor.getUser() != null ? doctor.getUser().getEmail() : null;
        Long departmentId = doctor.getDepartment() != null ? doctor.getDepartment().getId() : null;
        String departmentName = doctor.getDepartment() != null ? doctor.getDepartment().getName() : null;

        return new DoctorResponse(
                doctor.getId(),
                doctor.getUser() != null ? doctor.getUser().getId() : null,
                name,
                email,
                doctor.getDoctorReference(),
                departmentId,
                departmentName,
                doctor.getSpecialization(),
                doctor.getConsultationFee(),
                doctor.isActive(),
                doctor.getCreatedAt()
        );
    }
}
