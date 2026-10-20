package com.careflow.queue;

import com.careflow.appointment.entity.Appointment;
import com.careflow.appointment.entity.AppointmentStatus;
import com.careflow.appointment.repository.AppointmentRepository;
import com.careflow.audit.service.AuditService;
import com.careflow.common.exception.BusinessValidationException;
import com.careflow.common.exception.InvalidStateTransitionException;
import com.careflow.doctor.entity.Doctor;
import com.careflow.doctor.repository.DoctorRepository;
import com.careflow.patient.entity.Patient;
import com.careflow.queue.dto.CheckInRequest;
import com.careflow.queue.entity.QueueEntry;
import com.careflow.queue.entity.QueueStatus;
import com.careflow.queue.repository.DoctorDailyQueueSequenceRepository;
import com.careflow.queue.repository.QueueEntryRepository;
import com.careflow.queue.service.QueueService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueueProgressionTest {

    @Mock
    private QueueEntryRepository queueEntryRepository;
    @Mock
    private DoctorDailyQueueSequenceRepository sequenceRepository;
    @Mock
    private AppointmentRepository appointmentRepository;
    @Mock
    private DoctorRepository doctorRepository;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private QueueService queueService;

    private Appointment todayAppointment;
    private Doctor doctor;
    private QueueEntry sampleEntry;

    @BeforeEach
    void setUp() {
        doctor = new Doctor();
        doctor.setId(1L);

        Patient patient = new Patient();
        patient.setId(2L);

        todayAppointment = new Appointment(
                "APT-TODAY", patient, doctor,
                LocalDate.now().atTime(10, 0), LocalDate.now().atTime(10, 30),
                "Checkup", "alice"
        );
        todayAppointment.setId(50L);
        todayAppointment.setStatus(AppointmentStatus.BOOKED);

        sampleEntry = new QueueEntry(todayAppointment, doctor, LocalDate.now(), 1);
        sampleEntry.setId(100L);
    }

    @Test
    @DisplayName("Should reject check-in if appointment date is not today")
    void shouldRejectCheckInForFutureDate() {
        Appointment futureAppt = new Appointment(
                "APT-FUTURE", null, doctor,
                LocalDate.now().plusDays(2).atTime(10, 0), LocalDate.now().plusDays(2).atTime(10, 30),
                "Future Checkup", "alice"
        );
        futureAppt.setId(51L);
        futureAppt.setStatus(AppointmentStatus.BOOKED);

        when(appointmentRepository.findById(51L)).thenReturn(Optional.of(futureAppt));

        assertThatThrownBy(() -> queueService.checkIn(new CheckInRequest(51L), "receptionist"))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessageContaining("Check-in is only allowed on the appointment date");
    }

    @Test
    @DisplayName("Valid progression: WAITING -> CALLED -> IN_CONSULTATION -> COMPLETED")
    void shouldProgressThroughCompleteQueueLifecycle() {
        // Step 1: Call patient (WAITING -> CALLED)
        sampleEntry.setStatus(QueueStatus.WAITING);
        when(queueEntryRepository.findById(100L)).thenReturn(Optional.of(sampleEntry));
        when(queueEntryRepository.save(any(QueueEntry.class))).thenAnswer(i -> i.getArgument(0));

        var calledRes = queueService.callNext(100L, "receptionist");
        assertThat(calledRes.status()).isEqualTo(QueueStatus.CALLED);
        assertThat(calledRes.calledAt()).isNotNull();

        // Step 2: Start consultation (CALLED -> IN_CONSULTATION)
        var consultRes = queueService.startConsultation(100L, "doctor");
        assertThat(consultRes.status()).isEqualTo(QueueStatus.IN_CONSULTATION);
        assertThat(todayAppointment.getStatus()).isEqualTo(AppointmentStatus.IN_CONSULTATION);

        // Step 3: Complete consultation (IN_CONSULTATION -> COMPLETED)
        var completeRes = queueService.completeConsultation(100L, "doctor");
        assertThat(completeRes.status()).isEqualTo(QueueStatus.COMPLETED);
        assertThat(completeRes.completedAt()).isNotNull();
        assertThat(todayAppointment.getStatus()).isEqualTo(AppointmentStatus.COMPLETED);
    }

    @Test
    @DisplayName("Should reject starting consultation on an already completed queue entry")
    void shouldRejectStartingConsultationWhenAlreadyCompleted() {
        sampleEntry.setStatus(QueueStatus.COMPLETED);
        when(queueEntryRepository.findById(100L)).thenReturn(Optional.of(sampleEntry));

        assertThatThrownBy(() -> queueService.startConsultation(100L, "doctor"))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessageContaining("Cannot start consultation for queue entry with status: COMPLETED");
    }
}
