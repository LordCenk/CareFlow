package com.careflow.appointment.dto;

import com.careflow.appointment.entity.Appointment;
import com.careflow.appointment.entity.AppointmentStatus;
import java.time.Instant;
import java.time.LocalDateTime;

public record AppointmentResponse(
        Long id,
        String appointmentReference,
        Long patientId,
        String patientName,
        String patientContact,
        Long doctorId,
        String doctorName,
        String doctorSpecialization,
        LocalDateTime startDateTime,
        LocalDateTime endDateTime,
        AppointmentStatus status,
        String reasonForVisit,
        String cancellationReason,
        String createdBy,
        Instant createdAt,
        Instant updatedAt
) {
    public static AppointmentResponse fromEntity(Appointment appointment) {
        String patientName = "Unknown Patient";
        String patientContact = null;
        if (appointment.getPatient() != null) {
            patientContact = appointment.getPatient().getContactNumber();
            if (appointment.getPatient().getUser() != null) {
                patientName = appointment.getPatient().getUser().getName();
            }
        }

        String doctorName = "Unknown Doctor";
        String doctorSpecialization = null;
        if (appointment.getDoctor() != null) {
            doctorSpecialization = appointment.getDoctor().getSpecialization();
            if (appointment.getDoctor().getUser() != null) {
                doctorName = appointment.getDoctor().getUser().getName();
            }
        }

        return new AppointmentResponse(
                appointment.getId(),
                appointment.getAppointmentReference(),
                appointment.getPatient() != null ? appointment.getPatient().getId() : null,
                patientName,
                patientContact,
                appointment.getDoctor() != null ? appointment.getDoctor().getId() : null,
                doctorName,
                doctorSpecialization,
                appointment.getStartDateTime(),
                appointment.getEndDateTime(),
                appointment.getStatus(),
                appointment.getReasonForVisit(),
                appointment.getCancellationReason(),
                appointment.getCreatedBy(),
                appointment.getCreatedAt(),
                appointment.getUpdatedAt()
        );
    }
}
