package com.careflow.scheduling.service;

import com.careflow.appointment.entity.Appointment;
import com.careflow.appointment.entity.AppointmentStatus;
import com.careflow.appointment.repository.AppointmentRepository;
import com.careflow.common.exception.BusinessValidationException;
import com.careflow.common.exception.ResourceNotFoundException;
import com.careflow.doctor.entity.Doctor;
import com.careflow.doctor.repository.DoctorRepository;
import com.careflow.scheduling.dto.*;
import com.careflow.scheduling.entity.DoctorAvailability;
import com.careflow.scheduling.entity.DoctorLeave;
import com.careflow.scheduling.entity.LeaveStatus;
import com.careflow.scheduling.repository.DoctorAvailabilityRepository;
import com.careflow.scheduling.repository.DoctorLeaveRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class DoctorScheduleService {

    private final DoctorAvailabilityRepository availabilityRepository;
    private final DoctorLeaveRepository leaveRepository;
    private final DoctorRepository doctorRepository;
    private final AppointmentRepository appointmentRepository;

    private static final Set<AppointmentStatus> BLOCKING_STATUSES = Set.of(
            AppointmentStatus.BOOKED,
            AppointmentStatus.CHECKED_IN,
            AppointmentStatus.IN_CONSULTATION
    );

    public DoctorScheduleService(
            DoctorAvailabilityRepository availabilityRepository,
            DoctorLeaveRepository leaveRepository,
            DoctorRepository doctorRepository,
            AppointmentRepository appointmentRepository
    ) {
        this.availabilityRepository = availabilityRepository;
        this.leaveRepository = leaveRepository;
        this.doctorRepository = doctorRepository;
        this.appointmentRepository = appointmentRepository;
    }

    @Transactional
    public AvailabilityResponse addAvailability(Long doctorId, CreateAvailabilityRequest request) {
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor", "id", doctorId));

        if (!request.startTime().isBefore(request.endTime())) {
            throw new BusinessValidationException("Availability startTime must be before endTime");
        }

        int duration = request.slotDurationMinutes() != null ? request.slotDurationMinutes() : 30;
        if (duration <= 0) {
            throw new BusinessValidationException("Slot duration must be greater than zero");
        }

        DoctorAvailability availability = new DoctorAvailability(
                doctor,
                request.dayOfWeek(),
                request.startTime(),
                request.endTime(),
                duration,
                request.effectiveFrom(),
                request.effectiveUntil()
        );

        DoctorAvailability saved = availabilityRepository.save(availability);
        return AvailabilityResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public List<AvailabilityResponse> getDoctorAvailability(Long doctorId) {
        if (!doctorRepository.existsById(doctorId)) {
            throw new ResourceNotFoundException("Doctor", "id", doctorId);
        }
        return availabilityRepository.findByDoctorIdAndActiveTrue(doctorId)
                .stream()
                .map(AvailabilityResponse::fromEntity)
                .toList();
    }

    @Transactional
    public LeaveResponse addLeave(Long doctorId, CreateLeaveRequest request) {
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor", "id", doctorId));

        if (!request.startDateTime().isBefore(request.endDateTime())) {
            throw new BusinessValidationException("Leave startDateTime must be before endDateTime");
        }

        DoctorLeave leave = new DoctorLeave(
                doctor,
                request.startDateTime(),
                request.endDateTime(),
                request.reason(),
                LeaveStatus.APPROVED
        );

        DoctorLeave saved = leaveRepository.save(leave);
        return LeaveResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public List<LeaveResponse> getDoctorLeaves(Long doctorId) {
        if (!doctorRepository.existsById(doctorId)) {
            throw new ResourceNotFoundException("Doctor", "id", doctorId);
        }
        return leaveRepository.findByDoctorId(doctorId)
                .stream()
                .map(LeaveResponse::fromEntity)
                .toList();
    }

    /**
     * Computes real-time available appointment slots for a given doctor on a requested date.
     * Evaluates working hours, slot interval alignment, leave periods, and active bookings.
     */
    @Transactional(readOnly = true)
    public List<AvailableSlotResponse> getAvailableSlots(Long doctorId, LocalDate date) {
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor", "id", doctorId));

        if (!doctor.isActive()) {
            return List.of();
        }

        DayOfWeek dayOfWeek = date.getDayOfWeek();
        List<DoctorAvailability> availabilities = availabilityRepository.findByDoctorIdAndDayOfWeekAndActiveTrue(doctorId, dayOfWeek);

        if (availabilities.isEmpty()) {
            return List.of();
        }

        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();

        List<DoctorLeave> leaves = leaveRepository.findOverlappingLeaves(doctorId, dayStart, dayEnd, LeaveStatus.APPROVED);
        List<Appointment> existingAppointments = appointmentRepository.findDoctorAppointmentsForDay(doctorId, dayStart, dayEnd, BLOCKING_STATUSES);

        LocalDateTime now = LocalDateTime.now();
        List<AvailableSlotResponse> generatedSlots = new ArrayList<>();

        for (DoctorAvailability avail : availabilities) {
            LocalTime current = avail.getStartTime();
            int duration = avail.getSlotDurationMinutes();

            while (current.plusMinutes(duration).compareTo(avail.getEndTime()) <= 0) {
                LocalDateTime slotStart = date.atTime(current);
                LocalDateTime slotEnd = slotStart.plusMinutes(duration);

                // Slot must be in the future
                boolean isFuture = slotStart.isAfter(now);

                // Check conflict against doctor leave
                boolean conflictsWithLeave = leaves.stream().anyMatch(leave ->
                        leave.getStartDateTime().isBefore(slotEnd) && leave.getEndDateTime().isAfter(slotStart)
                );

                // Check conflict against existing active appointments
                boolean conflictsWithAppointment = existingAppointments.stream().anyMatch(appt ->
                        appt.getStartDateTime().isBefore(slotEnd) && appt.getEndDateTime().isAfter(slotStart)
                );

                boolean isSlotAvailable = isFuture && !conflictsWithLeave && !conflictsWithAppointment;

                generatedSlots.add(new AvailableSlotResponse(slotStart, slotEnd, duration, isSlotAvailable));

                current = current.plusMinutes(duration);
            }
        }

        return generatedSlots;
    }
}
