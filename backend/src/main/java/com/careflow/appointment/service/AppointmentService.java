package com.careflow.appointment.service;

import com.careflow.appointment.dto.*;
import com.careflow.appointment.entity.Appointment;
import com.careflow.appointment.entity.AppointmentStatus;
import com.careflow.appointment.repository.AppointmentRepository;
import com.careflow.audit.service.AuditService;
import com.careflow.common.exception.AppointmentConflictException;
import com.careflow.common.exception.BusinessValidationException;
import com.careflow.common.exception.InvalidStateTransitionException;
import com.careflow.common.exception.ResourceNotFoundException;
import com.careflow.common.response.PagedResponse;
import com.careflow.doctor.entity.Doctor;
import com.careflow.doctor.repository.DoctorRepository;
import com.careflow.notification.service.NotificationService;
import com.careflow.patient.entity.Patient;
import com.careflow.patient.repository.PatientRepository;
import com.careflow.queue.entity.QueueEntry;
import com.careflow.queue.entity.QueueStatus;
import com.careflow.queue.repository.QueueEntryRepository;
import com.careflow.scheduling.entity.DoctorAvailability;
import com.careflow.scheduling.entity.DoctorLeave;
import com.careflow.scheduling.entity.LeaveStatus;
import com.careflow.scheduling.repository.DoctorAvailabilityRepository;
import com.careflow.scheduling.repository.DoctorLeaveRepository;
import com.careflow.security.CustomUserPrincipal;
import com.careflow.user.entity.UserRole;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;
    private final DoctorAvailabilityRepository availabilityRepository;
    private final DoctorLeaveRepository leaveRepository;
    private final QueueEntryRepository queueEntryRepository;
    private final NotificationService notificationService;
    private final AuditService auditService;

    private final int cancellationDeadlineHours;
    private final int maxAdvanceBookingDays;

    private static final Set<AppointmentStatus> BLOCKING_STATUSES = Set.of(
            AppointmentStatus.BOOKED,
            AppointmentStatus.CHECKED_IN,
            AppointmentStatus.IN_CONSULTATION
    );

    public AppointmentService(
            AppointmentRepository appointmentRepository,
            DoctorRepository doctorRepository,
            PatientRepository patientRepository,
            DoctorAvailabilityRepository availabilityRepository,
            DoctorLeaveRepository leaveRepository,
            QueueEntryRepository queueEntryRepository,
            NotificationService notificationService,
            AuditService auditService,
            @Value("${careflow.scheduling.cancellation-deadline-hours:2}") int cancellationDeadlineHours,
            @Value("${careflow.scheduling.max-advance-booking-days:60}") int maxAdvanceBookingDays
    ) {
        this.appointmentRepository = appointmentRepository;
        this.doctorRepository = doctorRepository;
        this.patientRepository = patientRepository;
        this.availabilityRepository = availabilityRepository;
        this.leaveRepository = leaveRepository;
        this.queueEntryRepository = queueEntryRepository;
        this.notificationService = notificationService;
        this.auditService = auditService;
        this.cancellationDeadlineHours = cancellationDeadlineHours;
        this.maxAdvanceBookingDays = maxAdvanceBookingDays;
    }

    /**
     * Books an appointment using pessimistic write locks on the doctor and patient records.
     * This serializes slot reservations for the same doctor, completely eliminating race conditions.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AppointmentResponse bookAppointment(BookAppointmentRequest request, String performedBy) {
        validateAppointmentTiming(request.startDateTime(), request.endDateTime());

        // Acquire pessimistic write lock on the doctor so concurrent requests cannot book overlapping slots
        Doctor doctor = doctorRepository.findByIdWithLock(request.doctorId())
                .orElseThrow(() -> new ResourceNotFoundException("Doctor", "id", request.doctorId()));

        if (!doctor.isActive()) {
            throw new BusinessValidationException("Cannot book an appointment with an inactive doctor");
        }

        // Acquire pessimistic write lock on the patient to prevent double-booking for the same patient
        Patient patient = patientRepository.findByIdWithLock(request.patientId())
                .orElseThrow(() -> new ResourceNotFoundException("Patient", "id", request.patientId()));

        // Validate that requested time interval falls within doctor's configured working hours
        validateDoctorWorkingHours(doctor.getId(), request.startDateTime(), request.endDateTime());

        // Check if doctor is on approved leave
        List<DoctorLeave> overlappingLeaves = leaveRepository.findOverlappingLeaves(
                doctor.getId(), request.startDateTime(), request.endDateTime(), LeaveStatus.APPROVED
        );
        if (!overlappingLeaves.isEmpty()) {
            throw new AppointmentConflictException("Doctor is on approved leave during the requested appointment slot");
        }

        // Check if doctor already has an active overlapping appointment
        List<Appointment> conflictingDoctorAppointments = appointmentRepository.findConflictingDoctorAppointments(
                doctor.getId(), request.startDateTime(), request.endDateTime(), BLOCKING_STATUSES
        );
        if (!conflictingDoctorAppointments.isEmpty()) {
            throw new AppointmentConflictException("The requested appointment slot is already booked or overlaps with an existing appointment");
        }

        // Check if patient already has an active overlapping appointment
        List<Appointment> conflictingPatientAppointments = appointmentRepository.findConflictingPatientAppointments(
                patient.getId(), request.startDateTime(), request.endDateTime(), BLOCKING_STATUSES
        );
        if (!conflictingPatientAppointments.isEmpty()) {
            throw new AppointmentConflictException("Patient already has an active appointment scheduled during this time window");
        }

        String reference = generateAppointmentReference();
        Appointment appointment = new Appointment(
                reference,
                patient,
                doctor,
                request.startDateTime(),
                request.endDateTime(),
                request.reasonForVisit(),
                performedBy
        );

        Appointment saved = appointmentRepository.save(appointment);

        // Send confirmation alert and record audit trail
        notificationService.sendBookingConfirmation(saved);
        auditService.recordAudit("APPOINTMENT", saved.getId(), "BOOKED", performedBy,
                "Booked appointment from " + request.startDateTime() + " to " + request.endDateTime());

        return AppointmentResponse.fromEntity(saved);
    }

    /**
     * Atomically reschedules an appointment to a new time slot under pessimistic lock.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AppointmentResponse rescheduleAppointment(Long appointmentId, RescheduleAppointmentRequest request, String performedBy) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", "id", appointmentId));

        if (appointment.getStatus() != AppointmentStatus.BOOKED) {
            throw new InvalidStateTransitionException("Only appointments in BOOKED status can be rescheduled. Current status: " + appointment.getStatus());
        }

        validateAppointmentTiming(request.newStartDateTime(), request.newEndDateTime());

        // Acquire pessimistic write lock on doctor and patient
        Doctor doctor = doctorRepository.findByIdWithLock(appointment.getDoctor().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Doctor", "id", appointment.getDoctor().getId()));
        Patient patient = patientRepository.findByIdWithLock(appointment.getPatient().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Patient", "id", appointment.getPatient().getId()));

        validateDoctorWorkingHours(doctor.getId(), request.newStartDateTime(), request.newEndDateTime());

        // Check leaves
        List<DoctorLeave> leaves = leaveRepository.findOverlappingLeaves(
                doctor.getId(), request.newStartDateTime(), request.newEndDateTime(), LeaveStatus.APPROVED
        );
        if (!leaves.isEmpty()) {
            throw new AppointmentConflictException("Doctor is on approved leave during the rescheduled time slot");
        }

        // Check doctor conflicts excluding current appointment
        boolean doctorConflict = appointmentRepository.findConflictingDoctorAppointments(
                doctor.getId(), request.newStartDateTime(), request.newEndDateTime(), BLOCKING_STATUSES
        ).stream().anyMatch(a -> !a.getId().equals(appointmentId));

        if (doctorConflict) {
            throw new AppointmentConflictException("The requested rescheduled slot is already booked by another appointment");
        }

        // Check patient conflicts excluding current appointment
        boolean patientConflict = appointmentRepository.findConflictingPatientAppointments(
                patient.getId(), request.newStartDateTime(), request.newEndDateTime(), BLOCKING_STATUSES
        ).stream().anyMatch(a -> !a.getId().equals(appointmentId));

        if (patientConflict) {
            throw new AppointmentConflictException("Patient already has another appointment booked during the rescheduled time");
        }

        appointment.setStartDateTime(request.newStartDateTime());
        appointment.setEndDateTime(request.newEndDateTime());
        Appointment updated = appointmentRepository.save(appointment);

        notificationService.sendRescheduleNotice(updated);
        auditService.recordAudit("APPOINTMENT", updated.getId(), "RESCHEDULED", performedBy,
                "Rescheduled to " + request.newStartDateTime() + ". Reason: " + request.reason());

        return AppointmentResponse.fromEntity(updated);
    }

    /**
     * Cancels an appointment, enforcing role permissions, cancellation deadlines, and slot release.
     */
    @Transactional
    public AppointmentResponse cancelAppointment(Long appointmentId, CancelAppointmentRequest request, CustomUserPrincipal principal) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", "id", appointmentId));

        validateCancellationPermission(appointment, principal);

        if (appointment.getStatus() == AppointmentStatus.CANCELLED) {
            throw new InvalidStateTransitionException("Appointment is already cancelled");
        }
        if (appointment.getStatus() == AppointmentStatus.COMPLETED) {
            throw new InvalidStateTransitionException("Completed appointments cannot be cancelled");
        }

        // Check cancellation deadline for patients self-cancelling
        if (principal.getRole() == UserRole.PATIENT) {
            LocalDateTime deadline = appointment.getStartDateTime().minusHours(cancellationDeadlineHours);
            if (LocalDateTime.now().isAfter(deadline)) {
                throw new BusinessValidationException(String.format(
                        "Appointments cannot be cancelled less than %d hours before the appointment time. Please call the reception desk.",
                        cancellationDeadlineHours
                ));
            }
        }

        appointment.setStatus(AppointmentStatus.CANCELLED);
        appointment.setCancellationReason(request.cancellationReason());
        Appointment saved = appointmentRepository.save(appointment);

        // If patient had already checked in to queue, cancel queue entry
        Optional<QueueEntry> queueEntryOpt = queueEntryRepository.findByAppointmentId(appointmentId);
        if (queueEntryOpt.isPresent()) {
            QueueEntry queueEntry = queueEntryOpt.get();
            if (queueEntry.getStatus() == QueueStatus.WAITING || queueEntry.getStatus() == QueueStatus.CALLED) {
                queueEntry.setStatus(QueueStatus.CANCELLED);
                queueEntryRepository.save(queueEntry);
            }
        }

        notificationService.sendCancellationNotice(saved, request.cancellationReason());
        auditService.recordAudit("APPOINTMENT", saved.getId(), "CANCELLED", principal.getUsername(),
                "Reason: " + request.cancellationReason());

        return AppointmentResponse.fromEntity(saved);
    }

    /**
     * State machine transitions for appointments.
     */
    @Transactional
    public AppointmentResponse updateStatus(Long appointmentId, AppointmentStatus newStatus, CustomUserPrincipal principal) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", "id", appointmentId));

        AppointmentStatus current = appointment.getStatus();

        // Validate allowed state transitions
        boolean validTransition = switch (current) {
            case BOOKED -> newStatus == AppointmentStatus.CHECKED_IN
                    || newStatus == AppointmentStatus.CANCELLED
                    || newStatus == AppointmentStatus.NO_SHOW;
            case CHECKED_IN -> newStatus == AppointmentStatus.IN_CONSULTATION
                    || newStatus == AppointmentStatus.CANCELLED
                    || newStatus == AppointmentStatus.NO_SHOW;
            case IN_CONSULTATION -> newStatus == AppointmentStatus.COMPLETED;
            case COMPLETED, CANCELLED, NO_SHOW -> false;
        };

        if (!validTransition) {
            throw new InvalidStateTransitionException(String.format(
                    "Invalid appointment state transition from %s to %s", current, newStatus
            ));
        }

        // Validate role authority for transitions
        if (newStatus == AppointmentStatus.IN_CONSULTATION || newStatus == AppointmentStatus.COMPLETED) {
            if (principal.getRole() == UserRole.PATIENT) {
                throw new AccessDeniedException("Patients are not authorized to transition appointment to " + newStatus);
            }
        }

        appointment.setStatus(newStatus);
        Appointment saved = appointmentRepository.save(appointment);

        auditService.recordAudit("APPOINTMENT", saved.getId(), "STATUS_CHANGE", principal.getUsername(),
                "Transitioned from " + current + " to " + newStatus);

        return AppointmentResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public AppointmentResponse getAppointmentById(Long id, CustomUserPrincipal principal) {
        Appointment appointment = appointmentRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", "id", id));

        // Enforce ownership checks
        if (principal.getRole() == UserRole.PATIENT) {
            if (appointment.getPatient().getUser() == null || !appointment.getPatient().getUser().getId().equals(principal.getId())) {
                throw new AccessDeniedException("You do not have permission to view this appointment");
            }
        } else if (principal.getRole() == UserRole.DOCTOR) {
            if (appointment.getDoctor().getUser() == null || !appointment.getDoctor().getUser().getId().equals(principal.getId())) {
                throw new AccessDeniedException("Doctors can only access appointments assigned to them");
            }
        }

        return AppointmentResponse.fromEntity(appointment);
    }

    @Transactional(readOnly = true)
    public PagedResponse<AppointmentResponse> getAppointmentsForPatient(Long patientId, Pageable pageable) {
        Page<AppointmentResponse> page = appointmentRepository.findByPatientId(patientId, pageable)
                .map(AppointmentResponse::fromEntity);
        return PagedResponse.from(page);
    }

    @Transactional(readOnly = true)
    public PagedResponse<AppointmentResponse> getAppointmentsForDoctor(Long doctorId, Pageable pageable) {
        Page<AppointmentResponse> page = appointmentRepository.findByDoctorId(doctorId, pageable)
                .map(AppointmentResponse::fromEntity);
        return PagedResponse.from(page);
    }

    @Transactional(readOnly = true)
    public PagedResponse<AppointmentResponse> getAllAppointments(Pageable pageable) {
        Page<AppointmentResponse> page = appointmentRepository.findAll(pageable)
                .map(AppointmentResponse::fromEntity);
        return PagedResponse.from(page);
    }

    public Appointment getAppointmentEntity(Long id) {
        return appointmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", "id", id));
    }

    private void validateAppointmentTiming(LocalDateTime start, LocalDateTime end) {
        if (!start.isBefore(end)) {
            throw new BusinessValidationException("Appointment start time must be strictly before end time");
        }
        if (start.isBefore(LocalDateTime.now())) {
            throw new BusinessValidationException("Cannot book appointments in the past");
        }
        if (start.isAfter(LocalDateTime.now().plusDays(maxAdvanceBookingDays))) {
            throw new BusinessValidationException("Appointments cannot be booked more than " + maxAdvanceBookingDays + " days in advance");
        }
    }

    private void validateDoctorWorkingHours(Long doctorId, LocalDateTime start, LocalDateTime end) {
        List<DoctorAvailability> availabilities = availabilityRepository.findByDoctorIdAndDayOfWeekAndActiveTrue(
                doctorId, start.getDayOfWeek()
        );

        if (availabilities.isEmpty()) {
            throw new AppointmentConflictException("Doctor has no scheduled working hours on " + start.getDayOfWeek());
        }

        boolean fitsInSchedule = availabilities.stream().anyMatch(avail ->
                !start.toLocalTime().isBefore(avail.getStartTime()) &&
                !end.toLocalTime().isAfter(avail.getEndTime()) &&
                Duration.between(start, end).toMinutes() == avail.getSlotDurationMinutes()
        );

        if (!fitsInSchedule) {
            throw new AppointmentConflictException(
                    "Requested appointment interval does not align with doctor's working schedule or slot duration on " + start.getDayOfWeek()
            );
        }
    }

    private void validateCancellationPermission(Appointment appointment, CustomUserPrincipal principal) {
        if (principal.getRole() == UserRole.ADMIN || principal.getRole() == UserRole.RECEPTIONIST) {
            return;
        }
        if (principal.getRole() == UserRole.PATIENT) {
            if (appointment.getPatient().getUser() != null && appointment.getPatient().getUser().getId().equals(principal.getId())) {
                return;
            }
        }
        if (principal.getRole() == UserRole.DOCTOR) {
            if (appointment.getDoctor().getUser() != null && appointment.getDoctor().getUser().getId().equals(principal.getId())) {
                return;
            }
        }
        throw new AccessDeniedException("You do not have permission to cancel this appointment");
    }

    private String generateAppointmentReference() {
        int year = Year.now().getValue();
        String shortUuid = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return String.format("APT-%d-%s", year, shortUuid);
    }
}
