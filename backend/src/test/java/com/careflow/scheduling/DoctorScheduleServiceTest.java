package com.careflow.scheduling;

import com.careflow.appointment.entity.Appointment;
import com.careflow.appointment.entity.AppointmentStatus;
import com.careflow.appointment.repository.AppointmentRepository;
import com.careflow.department.entity.Department;
import com.careflow.doctor.entity.Doctor;
import com.careflow.doctor.repository.DoctorRepository;
import com.careflow.scheduling.dto.AvailableSlotResponse;
import com.careflow.scheduling.entity.DoctorAvailability;
import com.careflow.scheduling.entity.DoctorLeave;
import com.careflow.scheduling.entity.LeaveStatus;
import com.careflow.scheduling.repository.DoctorAvailabilityRepository;
import com.careflow.scheduling.repository.DoctorLeaveRepository;
import com.careflow.scheduling.service.DoctorScheduleService;
import com.careflow.user.entity.User;
import com.careflow.user.entity.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DoctorScheduleServiceTest {

    @Mock
    private DoctorAvailabilityRepository availabilityRepository;

    @Mock
    private DoctorLeaveRepository leaveRepository;

    @Mock
    private DoctorRepository doctorRepository;

    @Mock
    private AppointmentRepository appointmentRepository;

    @InjectMocks
    private DoctorScheduleService scheduleService;

    private Doctor sampleDoctor;

    @BeforeEach
    void setUp() {
        User user = new User("Dr. Gregory House", "house@careflow.com", "hash", UserRole.DOCTOR);
        user.setId(10L);
        Department dept = new Department("Diagnostics", "Complex illness diagnostics");
        dept.setId(1L);

        sampleDoctor = new Doctor(user, "DOC-2026-TEST", dept, "Nephrologist", BigDecimal.valueOf(100.00));
        sampleDoctor.setId(100L);
    }

    @Test
    @DisplayName("Should generate correct 30-minute slots based on working hours")
    void shouldGenerateAvailableSlotsForWorkingHours() {
        LocalDate futureDate = LocalDate.now().plusDays(5);
        // Ensure day is a Monday
        while (futureDate.getDayOfWeek() != DayOfWeek.MONDAY) {
            futureDate = futureDate.plusDays(1);
        }

        when(doctorRepository.findById(100L)).thenReturn(Optional.of(sampleDoctor));

        DoctorAvailability avail = new DoctorAvailability(
                sampleDoctor,
                DayOfWeek.MONDAY,
                LocalTime.of(9, 0),
                LocalTime.of(11, 0),
                30,
                null,
                null
        );
        when(availabilityRepository.findByDoctorIdAndDayOfWeekAndActiveTrue(100L, DayOfWeek.MONDAY))
                .thenReturn(List.of(avail));
        when(leaveRepository.findOverlappingLeaves(eq(100L), any(), any(), eq(LeaveStatus.APPROVED)))
                .thenReturn(List.of());
        when(appointmentRepository.findDoctorAppointmentsForDay(eq(100L), any(), any(), any()))
                .thenReturn(List.of());

        List<AvailableSlotResponse> slots = scheduleService.getAvailableSlots(100L, futureDate);

        // 9:00-9:30, 9:30-10:00, 10:00-10:30, 10:30-11:00 -> 4 slots
        assertThat(slots).hasSize(4);
        assertThat(slots).allMatch(AvailableSlotResponse::available);
        assertThat(slots.get(0).startDateTime().toLocalTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(slots.get(3).endDateTime().toLocalTime()).isEqualTo(LocalTime.of(11, 0));
    }

    @Test
    @DisplayName("Should mark slot as unavailable if doctor has approved leave during that time")
    void shouldMarkSlotUnavailableWhenDoctorOnLeave() {
        LocalDate futureDate = LocalDate.now().plusDays(5);
        while (futureDate.getDayOfWeek() != DayOfWeek.MONDAY) {
            futureDate = futureDate.plusDays(1);
        }

        when(doctorRepository.findById(100L)).thenReturn(Optional.of(sampleDoctor));

        DoctorAvailability avail = new DoctorAvailability(
                sampleDoctor,
                DayOfWeek.MONDAY,
                LocalTime.of(9, 0),
                LocalTime.of(10, 0),
                30,
                null,
                null
        );
        when(availabilityRepository.findByDoctorIdAndDayOfWeekAndActiveTrue(100L, DayOfWeek.MONDAY))
                .thenReturn(List.of(avail));

        // Leave from 09:00 to 09:30
        DoctorLeave leave = new DoctorLeave(
                sampleDoctor,
                futureDate.atTime(9, 0),
                futureDate.atTime(9, 30),
                "Conference",
                LeaveStatus.APPROVED
        );
        when(leaveRepository.findOverlappingLeaves(eq(100L), any(), any(), eq(LeaveStatus.APPROVED)))
                .thenReturn(List.of(leave));
        when(appointmentRepository.findDoctorAppointmentsForDay(eq(100L), any(), any(), any()))
                .thenReturn(List.of());

        List<AvailableSlotResponse> slots = scheduleService.getAvailableSlots(100L, futureDate);

        assertThat(slots).hasSize(2);
        // Slot 1 (9:00-9:30) conflicts with leave
        assertThat(slots.get(0).available()).isFalse();
        // Slot 2 (9:30-10:00) is free
        assertThat(slots.get(1).available()).isTrue();
    }

    @Test
    @DisplayName("Should mark slot as unavailable if an active appointment already occupies the slot")
    void shouldMarkSlotUnavailableWhenExistingAppointmentBlocksIt() {
        LocalDate futureDate = LocalDate.now().plusDays(5);
        while (futureDate.getDayOfWeek() != DayOfWeek.MONDAY) {
            futureDate = futureDate.plusDays(1);
        }

        when(doctorRepository.findById(100L)).thenReturn(Optional.of(sampleDoctor));

        DoctorAvailability avail = new DoctorAvailability(
                sampleDoctor,
                DayOfWeek.MONDAY,
                LocalTime.of(9, 0),
                LocalTime.of(10, 0),
                30,
                null,
                null
        );
        when(availabilityRepository.findByDoctorIdAndDayOfWeekAndActiveTrue(100L, DayOfWeek.MONDAY))
                .thenReturn(List.of(avail));
        when(leaveRepository.findOverlappingLeaves(eq(100L), any(), any(), eq(LeaveStatus.APPROVED)))
                .thenReturn(List.of());

        // Existing booked appointment for 09:30 - 10:00
        Appointment appt = new Appointment(
                "APT-TEST-001", null, sampleDoctor,
                futureDate.atTime(9, 30), futureDate.atTime(10, 0),
                "Consultation", "admin"
        );
        appt.setStatus(AppointmentStatus.BOOKED);

        when(appointmentRepository.findDoctorAppointmentsForDay(eq(100L), any(), any(), any()))
                .thenReturn(List.of(appt));

        List<AvailableSlotResponse> slots = scheduleService.getAvailableSlots(100L, futureDate);

        assertThat(slots).hasSize(2);
        // Slot 1 (9:00-9:30) is free
        assertThat(slots.get(0).available()).isTrue();
        // Slot 2 (9:30-10:00) is booked
        assertThat(slots.get(1).available()).isFalse();
    }
}
