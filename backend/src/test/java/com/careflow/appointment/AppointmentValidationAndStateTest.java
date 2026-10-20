package com.careflow.appointment;

import com.careflow.appointment.dto.CancelAppointmentRequest;
import com.careflow.appointment.dto.UpdateAppointmentStatusRequest;
import com.careflow.appointment.entity.Appointment;
import com.careflow.appointment.entity.AppointmentStatus;
import com.careflow.appointment.repository.AppointmentRepository;
import com.careflow.appointment.service.AppointmentService;
import com.careflow.audit.service.AuditService;
import com.careflow.common.exception.BusinessValidationException;
import com.careflow.common.exception.InvalidStateTransitionException;
import com.careflow.doctor.entity.Doctor;
import com.careflow.doctor.repository.DoctorRepository;
import com.careflow.notification.service.NotificationService;
import com.careflow.patient.entity.Patient;
import com.careflow.patient.repository.PatientRepository;
import com.careflow.queue.repository.QueueEntryRepository;
import com.careflow.scheduling.repository.DoctorAvailabilityRepository;
import com.careflow.scheduling.repository.DoctorLeaveRepository;
import com.careflow.security.CustomUserPrincipal;
import com.careflow.user.entity.User;
import com.careflow.user.entity.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentValidationAndStateTest {

    @Mock
    private AppointmentRepository appointmentRepository;
    @Mock
    private DoctorRepository doctorRepository;
    @Mock
    private PatientRepository patientRepository;
    @Mock
    private DoctorAvailabilityRepository availabilityRepository;
    @Mock
    private DoctorLeaveRepository leaveRepository;
    @Mock
    private QueueEntryRepository queueEntryRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private AuditService auditService;

    private AppointmentService appointmentService;

    private Appointment sampleAppointment;
    private User patientUser;
    private CustomUserPrincipal patientPrincipal;
    private CustomUserPrincipal doctorPrincipal;

    @BeforeEach
    void setUp() {
        appointmentService = new AppointmentService(
                appointmentRepository,
                doctorRepository,
                patientRepository,
                availabilityRepository,
                leaveRepository,
                queueEntryRepository,
                notificationService,
                auditService,
                2,  // 2 hours cancellation deadline
                60  // 60 days max advance
        );

        patientUser = new User("Alice Patient", "alice@test.com", "hash", UserRole.PATIENT);
        patientUser.setId(5L);
        patientPrincipal = new CustomUserPrincipal(patientUser);

        Patient patient = new Patient(patientUser, "PAT-001", null, "12345", "Addr");
        patient.setId(1L);

        User docUser = new User("Dr. Gregory", "doc@test.com", "hash", UserRole.DOCTOR);
        docUser.setId(6L);
        doctorPrincipal = new CustomUserPrincipal(docUser);

        Doctor doctor = new Doctor(docUser, "DOC-001", null, "Surgery", null);
        doctor.setId(2L);

        sampleAppointment = new Appointment(
                "APT-2026-TEST", patient, doctor,
                LocalDateTime.now().plusHours(24), LocalDateTime.now().plusHours(24).plusMinutes(30),
                "Consultation", "alice"
        );
        sampleAppointment.setId(10L);
    }

    @Test
    @DisplayName("Valid transition: BOOKED -> CHECKED_IN")
    void shouldAllowTransitionFromBookedToCheckedIn() {
        sampleAppointment.setStatus(AppointmentStatus.BOOKED);
        when(appointmentRepository.findById(10L)).thenReturn(Optional.of(sampleAppointment));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(i -> i.getArgument(0));

        var res = appointmentService.updateStatus(10L, AppointmentStatus.CHECKED_IN, doctorPrincipal);

        assertThat(res.status()).isEqualTo(AppointmentStatus.CHECKED_IN);
    }

    @Test
    @DisplayName("Valid transition: CHECKED_IN -> IN_CONSULTATION")
    void shouldAllowTransitionFromCheckedInToInConsultation() {
        sampleAppointment.setStatus(AppointmentStatus.CHECKED_IN);
        when(appointmentRepository.findById(10L)).thenReturn(Optional.of(sampleAppointment));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(i -> i.getArgument(0));

        var res = appointmentService.updateStatus(10L, AppointmentStatus.IN_CONSULTATION, doctorPrincipal);

        assertThat(res.status()).isEqualTo(AppointmentStatus.IN_CONSULTATION);
    }

    @Test
    @DisplayName("Valid transition: IN_CONSULTATION -> COMPLETED")
    void shouldAllowTransitionFromInConsultationToCompleted() {
        sampleAppointment.setStatus(AppointmentStatus.IN_CONSULTATION);
        when(appointmentRepository.findById(10L)).thenReturn(Optional.of(sampleAppointment));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(i -> i.getArgument(0));

        var res = appointmentService.updateStatus(10L, AppointmentStatus.COMPLETED, doctorPrincipal);

        assertThat(res.status()).isEqualTo(AppointmentStatus.COMPLETED);
    }

    @Test
    @DisplayName("Invalid transition: BOOKED directly to COMPLETED should be rejected")
    void shouldRejectDirectTransitionFromBookedToCompleted() {
        sampleAppointment.setStatus(AppointmentStatus.BOOKED);
        when(appointmentRepository.findById(10L)).thenReturn(Optional.of(sampleAppointment));

        assertThatThrownBy(() -> appointmentService.updateStatus(10L, AppointmentStatus.COMPLETED, doctorPrincipal))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessageContaining("Invalid appointment state transition from BOOKED to COMPLETED");
    }

    @Test
    @DisplayName("Invalid transition: COMPLETED to CANCELLED should be rejected")
    void shouldRejectCancellingCompletedAppointment() {
        sampleAppointment.setStatus(AppointmentStatus.COMPLETED);
        when(appointmentRepository.findById(10L)).thenReturn(Optional.of(sampleAppointment));

        assertThatThrownBy(() -> appointmentService.cancelAppointment(10L, new CancelAppointmentRequest("Changed mind"), patientPrincipal))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessageContaining("Completed appointments cannot be cancelled");
    }

    @Test
    @DisplayName("Cancellation policy: Patient cannot self-cancel less than 2 hours before appointment")
    void shouldRejectPatientCancellationWithinDeadlineWindow() {
        // Appointment is scheduled for 1 hour from now (within the 2h deadline)
        sampleAppointment.setStartDateTime(LocalDateTime.now().plusHours(1));
        sampleAppointment.setStatus(AppointmentStatus.BOOKED);
        when(appointmentRepository.findById(10L)).thenReturn(Optional.of(sampleAppointment));

        assertThatThrownBy(() -> appointmentService.cancelAppointment(10L, new CancelAppointmentRequest("Late cancel"), patientPrincipal))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessageContaining("Appointments cannot be cancelled less than 2 hours");
    }
}
