package com.careflow.queue.dto;

import com.careflow.queue.entity.QueueEntry;
import com.careflow.queue.entity.QueueStatus;
import java.time.Instant;
import java.time.LocalDate;

public record QueueEntryResponse(
        Long id,
        Long appointmentId,
        String appointmentReference,
        Long patientId,
        String patientName,
        Long doctorId,
        String doctorName,
        LocalDate queueDate,
        int queueNumber,
        QueueStatus status,
        Instant checkedInAt,
        Instant calledAt,
        Instant completedAt
) {
    public static QueueEntryResponse fromEntity(QueueEntry entry) {
        String patientName = "Unknown Patient";
        Long patientId = null;
        String apptRef = null;

        if (entry.getAppointment() != null) {
            apptRef = entry.getAppointment().getAppointmentReference();
            if (entry.getAppointment().getPatient() != null) {
                patientId = entry.getAppointment().getPatient().getId();
                if (entry.getAppointment().getPatient().getUser() != null) {
                    patientName = entry.getAppointment().getPatient().getUser().getName();
                }
            }
        }

        String doctorName = entry.getDoctor() != null && entry.getDoctor().getUser() != null
                ? entry.getDoctor().getUser().getName() : "Unknown Doctor";

        return new QueueEntryResponse(
                entry.getId(),
                entry.getAppointment() != null ? entry.getAppointment().getId() : null,
                apptRef,
                patientId,
                patientName,
                entry.getDoctor() != null ? entry.getDoctor().getId() : null,
                doctorName,
                entry.getQueueDate(),
                entry.getQueueNumber(),
                entry.getStatus(),
                entry.getCheckedInAt(),
                entry.getCalledAt(),
                entry.getCompletedAt()
        );
    }
}
