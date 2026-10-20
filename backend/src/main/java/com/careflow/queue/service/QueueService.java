package com.careflow.queue.service;

import com.careflow.appointment.entity.Appointment;
import com.careflow.appointment.entity.AppointmentStatus;
import com.careflow.appointment.repository.AppointmentRepository;
import com.careflow.audit.service.AuditService;
import com.careflow.common.exception.BusinessValidationException;
import com.careflow.common.exception.InvalidStateTransitionException;
import com.careflow.common.exception.ResourceNotFoundException;
import com.careflow.doctor.entity.Doctor;
import com.careflow.doctor.repository.DoctorRepository;
import com.careflow.queue.dto.CheckInRequest;
import com.careflow.queue.dto.DoctorQueueSummaryResponse;
import com.careflow.queue.dto.QueueEntryResponse;
import com.careflow.queue.entity.DoctorDailyQueueSequence;
import com.careflow.queue.entity.DoctorDailyQueueSequence.DoctorDailyQueueSequenceId;
import com.careflow.queue.entity.QueueEntry;
import com.careflow.queue.entity.QueueStatus;
import com.careflow.queue.repository.DoctorDailyQueueSequenceRepository;
import com.careflow.queue.repository.QueueEntryRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class QueueService {

    private final QueueEntryRepository queueEntryRepository;
    private final DoctorDailyQueueSequenceRepository sequenceRepository;
    private final AppointmentRepository appointmentRepository;
    private final DoctorRepository doctorRepository;
    private final AuditService auditService;

    public QueueService(
            QueueEntryRepository queueEntryRepository,
            DoctorDailyQueueSequenceRepository sequenceRepository,
            AppointmentRepository appointmentRepository,
            DoctorRepository doctorRepository,
            AuditService auditService
    ) {
        this.queueEntryRepository = queueEntryRepository;
        this.sequenceRepository = sequenceRepository;
        this.appointmentRepository = appointmentRepository;
        this.doctorRepository = doctorRepository;
        this.auditService = auditService;
    }

    /**
     * Checks in a patient for their scheduled appointment today.
     * Uses pessimistic locking on the daily doctor queue sequence counter
     * to safely allocate strictly sequential, non-colliding queue numbers.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public QueueEntryResponse checkIn(CheckInRequest request, String performedBy) {
        Appointment appointment = appointmentRepository.findById(request.appointmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", "id", request.appointmentId()));

        if (appointment.getStatus() != AppointmentStatus.BOOKED) {
            throw new InvalidStateTransitionException("Only appointments in BOOKED status can be checked in. Current status: " + appointment.getStatus());
        }

        LocalDate today = LocalDate.now();
        if (!appointment.getStartDateTime().toLocalDate().equals(today)) {
            throw new BusinessValidationException(String.format(
                    "Check-in is only allowed on the appointment date. Appointment date: %s, Today: %s",
                    appointment.getStartDateTime().toLocalDate(), today
            ));
        }

        if (queueEntryRepository.existsByAppointmentId(appointment.getId())) {
            throw new BusinessValidationException("Patient has already checked in for this appointment");
        }

        Long doctorId = appointment.getDoctor().getId();
        // Acquire pessimistic write lock on the doctor to serialize queue allocation and eliminate concurrent insert collisions
        Doctor doctor = doctorRepository.findByIdWithLock(doctorId)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor", "id", doctorId));

        int queueNumber = allocateNextQueueNumber(doctorId, today);

        QueueEntry queueEntry = new QueueEntry(appointment, doctor, today, queueNumber);
        QueueEntry savedEntry = queueEntryRepository.save(queueEntry);

        // Update appointment status to CHECKED_IN
        appointment.setStatus(AppointmentStatus.CHECKED_IN);
        appointmentRepository.save(appointment);

        auditService.recordAudit("QUEUE", savedEntry.getId(), "CHECKED_IN", performedBy,
                "Checked in patient for appointment " + appointment.getAppointmentReference() + " with queue #" + queueNumber);

        return QueueEntryResponse.fromEntity(savedEntry);
    }

    /**
     * Calls the next waiting patient in the queue.
     */
    @Transactional
    public QueueEntryResponse callNext(Long queueEntryId, String performedBy) {
        QueueEntry entry = queueEntryRepository.findById(queueEntryId)
                .orElseThrow(() -> new ResourceNotFoundException("QueueEntry", "id", queueEntryId));

        if (entry.getStatus() != QueueStatus.WAITING) {
            throw new InvalidStateTransitionException("Cannot call patient with queue status: " + entry.getStatus());
        }

        entry.setStatus(QueueStatus.CALLED);
        entry.setCalledAt(Instant.now());
        QueueEntry updated = queueEntryRepository.save(entry);

        auditService.recordAudit("QUEUE", updated.getId(), "CALLED", performedBy,
                "Called queue #" + updated.getQueueNumber());

        return QueueEntryResponse.fromEntity(updated);
    }

    /**
     * Marks the consultation as started.
     */
    @Transactional
    public QueueEntryResponse startConsultation(Long queueEntryId, String performedBy) {
        QueueEntry entry = queueEntryRepository.findById(queueEntryId)
                .orElseThrow(() -> new ResourceNotFoundException("QueueEntry", "id", queueEntryId));

        if (entry.getStatus() != QueueStatus.CALLED && entry.getStatus() != QueueStatus.WAITING) {
            throw new InvalidStateTransitionException("Cannot start consultation for queue entry with status: " + entry.getStatus());
        }

        entry.setStatus(QueueStatus.IN_CONSULTATION);
        QueueEntry updated = queueEntryRepository.save(entry);

        Appointment appointment = entry.getAppointment();
        if (appointment != null) {
            appointment.setStatus(AppointmentStatus.IN_CONSULTATION);
            appointmentRepository.save(appointment);
        }

        auditService.recordAudit("QUEUE", updated.getId(), "IN_CONSULTATION", performedBy,
                "Consultation started for queue #" + updated.getQueueNumber());

        return QueueEntryResponse.fromEntity(updated);
    }

    /**
     * Marks the patient consultation as completed.
     */
    @Transactional
    public QueueEntryResponse completeConsultation(Long queueEntryId, String performedBy) {
        QueueEntry entry = queueEntryRepository.findById(queueEntryId)
                .orElseThrow(() -> new ResourceNotFoundException("QueueEntry", "id", queueEntryId));

        if (entry.getStatus() != QueueStatus.IN_CONSULTATION) {
            throw new InvalidStateTransitionException("Only consultations currently in progress can be marked completed. Current: " + entry.getStatus());
        }

        entry.setStatus(QueueStatus.COMPLETED);
        entry.setCompletedAt(Instant.now());
        QueueEntry updated = queueEntryRepository.save(entry);

        Appointment appointment = entry.getAppointment();
        if (appointment != null) {
            appointment.setStatus(AppointmentStatus.COMPLETED);
            appointmentRepository.save(appointment);
        }

        auditService.recordAudit("QUEUE", updated.getId(), "COMPLETED", performedBy,
                "Consultation completed for queue #" + updated.getQueueNumber());

        return QueueEntryResponse.fromEntity(updated);
    }

    /**
     * Marks a patient as a no-show if they fail to present when called.
     */
    @Transactional
    public QueueEntryResponse markNoShow(Long queueEntryId, String performedBy) {
        QueueEntry entry = queueEntryRepository.findById(queueEntryId)
                .orElseThrow(() -> new ResourceNotFoundException("QueueEntry", "id", queueEntryId));

        if (entry.getStatus() != QueueStatus.WAITING && entry.getStatus() != QueueStatus.CALLED) {
            throw new InvalidStateTransitionException("Cannot mark no-show for queue entry in status: " + entry.getStatus());
        }

        entry.setStatus(QueueStatus.NO_SHOW);
        QueueEntry updated = queueEntryRepository.save(entry);

        Appointment appointment = entry.getAppointment();
        if (appointment != null) {
            appointment.setStatus(AppointmentStatus.NO_SHOW);
            appointmentRepository.save(appointment);
        }

        auditService.recordAudit("QUEUE", updated.getId(), "NO_SHOW", performedBy,
                "Marked queue #" + updated.getQueueNumber() + " as no-show");

        return QueueEntryResponse.fromEntity(updated);
    }

    /**
     * Retrieves the daily queue overview for a doctor.
     */
    @Transactional(readOnly = true)
    public DoctorQueueSummaryResponse getQueueForDoctorAndDate(Long doctorId, LocalDate date) {
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor", "id", doctorId));

        List<QueueEntry> entries = queueEntryRepository.findByDoctorAndDate(doctorId, date);
        List<QueueEntryResponse> entryResponses = entries.stream().map(QueueEntryResponse::fromEntity).toList();

        int waiting = (int) entries.stream().filter(e -> e.getStatus() == QueueStatus.WAITING).count();
        int inConsultation = (int) entries.stream().filter(e -> e.getStatus() == QueueStatus.IN_CONSULTATION).count();
        int completed = (int) entries.stream().filter(e -> e.getStatus() == QueueStatus.COMPLETED).count();

        String doctorName = doctor.getUser() != null ? doctor.getUser().getName() : "Unknown Doctor";

        return new DoctorQueueSummaryResponse(
                doctorId,
                doctorName,
                date,
                entries.size(),
                waiting,
                inConsultation,
                completed,
                entryResponses
        );
    }

    /**
     * Atomically allocates the next daily queue number using pessimistic write lock.
     */
    private int allocateNextQueueNumber(Long doctorId, LocalDate queueDate) {
        DoctorDailyQueueSequenceId id = new DoctorDailyQueueSequenceId(doctorId, queueDate);
        Optional<DoctorDailyQueueSequence> sequenceOpt = sequenceRepository.findById(id);

        if (sequenceOpt.isPresent()) {
            DoctorDailyQueueSequence sequence = sequenceOpt.get();
            int nextNumber = sequence.getLastQueueNumber() + 1;
            sequence.setLastQueueNumber(nextNumber);
            sequenceRepository.save(sequence);
            return nextNumber;
        } else {
            DoctorDailyQueueSequence newSequence = new DoctorDailyQueueSequence(doctorId, queueDate, 1);
            sequenceRepository.save(newSequence);
            return 1;
        }
    }
}
