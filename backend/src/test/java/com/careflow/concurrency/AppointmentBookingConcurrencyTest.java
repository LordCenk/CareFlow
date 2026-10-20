package com.careflow.concurrency;

import com.careflow.appointment.dto.BookAppointmentRequest;
import com.careflow.appointment.entity.Appointment;
import com.careflow.appointment.entity.AppointmentStatus;
import com.careflow.appointment.repository.AppointmentRepository;
import com.careflow.appointment.service.AppointmentService;
import com.careflow.common.exception.AppointmentConflictException;
import com.careflow.doctor.entity.Doctor;
import com.careflow.doctor.repository.DoctorRepository;
import com.careflow.patient.entity.Patient;
import com.careflow.patient.repository.PatientRepository;
import com.careflow.queue.dto.CheckInRequest;
import com.careflow.queue.dto.QueueEntryResponse;
import com.careflow.queue.service.QueueService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AppointmentBookingConcurrencyTest {

    @Autowired
    private AppointmentService appointmentService;

    @Autowired
    private QueueService queueService;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private DoctorRepository doctorRepository;

    @Autowired
    private PatientRepository patientRepository;

    @Test
    @DisplayName("Concurrency: Simultaneous booking for the exact same doctor and slot results in exactly 1 success and 1 conflict")
    void shouldPreventDoubleBookingUnderConcurrentRequests() throws Exception {
        Long doctorId = 1L; // Dr. Smith
        Long patient1Id = 1L; // Alice
        Long patient2Id = 2L; // Bob

        LocalDate futureMonday = LocalDate.now().plusWeeks(3).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
        LocalDateTime slotStart = futureMonday.atTime(11, 0);
        LocalDateTime slotEnd = futureMonday.atTime(11, 30);

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        List<Throwable> exceptions = Collections.synchronizedList(new ArrayList<>());

        // Thread 1: Patient 1 books the slot
        executor.submit(() -> {
            readyLatch.countDown();
            try {
                startLatch.await();
                appointmentService.bookAppointment(
                        new BookAppointmentRequest(patient1Id, doctorId, slotStart, slotEnd, "Patient 1 routine checkup"),
                        "patient.alice@careflow.com"
                );
                successCount.incrementAndGet();
            } catch (AppointmentConflictException ex) {
                conflictCount.incrementAndGet();
            } catch (Throwable t) {
                exceptions.add(t);
            }
        });

        // Thread 2: Patient 2 attempts to book the EXACT SAME slot simultaneously
        executor.submit(() -> {
            readyLatch.countDown();
            try {
                startLatch.await();
                appointmentService.bookAppointment(
                        new BookAppointmentRequest(patient2Id, doctorId, slotStart, slotEnd, "Patient 2 consultation"),
                        "patient.bob@careflow.com"
                );
                successCount.incrementAndGet();
            } catch (AppointmentConflictException ex) {
                conflictCount.incrementAndGet();
            } catch (Throwable t) {
                exceptions.add(t);
            }
        });

        readyLatch.await(5, TimeUnit.SECONDS);
        // Release both threads simultaneously to race on the database
        startLatch.countDown();

        executor.shutdown();
        boolean finished = executor.awaitTermination(15, TimeUnit.SECONDS);
        assertThat(finished).isTrue();

        assertThat(exceptions).isEmpty();
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(1);

        // Verify that database holds strictly 1 active appointment for this slot
        List<Appointment> bookedAppointments = appointmentRepository.findConflictingDoctorAppointments(
                doctorId, slotStart, slotEnd, List.of(AppointmentStatus.BOOKED)
        );
        assertThat(bookedAppointments).hasSize(1);
    }

    @Test
    @DisplayName("Concurrency: Simultaneous check-in generates strictly unique, non-colliding queue numbers")
    void shouldAllocateUniqueQueueNumbersUnderConcurrentCheckIn() throws Exception {
        Doctor doctor = doctorRepository.findById(1L).orElseThrow();
        Patient patient1 = patientRepository.findById(1L).orElseThrow();
        Patient patient2 = patientRepository.findById(2L).orElseThrow();

        // Create 2 distinct appointments for today
        Appointment appt1 = new Appointment(
                "APT-CONC-Q1-" + System.currentTimeMillis(),
                patient1, doctor,
                LocalDate.now().atTime(12, 0), LocalDate.now().atTime(12, 30),
                "Consult 1", "admin"
        );
        appt1.setStatus(AppointmentStatus.BOOKED);
        Appointment saved1 = appointmentRepository.save(appt1);

        Appointment appt2 = new Appointment(
                "APT-CONC-Q2-" + System.currentTimeMillis(),
                patient2, doctor,
                LocalDate.now().atTime(12, 30), LocalDate.now().atTime(13, 0),
                "Consult 2", "admin"
        );
        appt2.setStatus(AppointmentStatus.BOOKED);
        Appointment saved2 = appointmentRepository.save(appt2);

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);

        List<QueueEntryResponse> responses = Collections.synchronizedList(new ArrayList<>());
        List<Throwable> exceptions = Collections.synchronizedList(new ArrayList<>());

        executor.submit(() -> {
            readyLatch.countDown();
            try {
                startLatch.await();
                QueueEntryResponse res = queueService.checkIn(new CheckInRequest(saved1.getId()), "receptionist");
                responses.add(res);
            } catch (Throwable t) {
                exceptions.add(t);
            }
        });

        executor.submit(() -> {
            readyLatch.countDown();
            try {
                startLatch.await();
                QueueEntryResponse res = queueService.checkIn(new CheckInRequest(saved2.getId()), "receptionist");
                responses.add(res);
            } catch (Throwable t) {
                exceptions.add(t);
            }
        });

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();

        executor.shutdown();
        boolean finished = executor.awaitTermination(15, TimeUnit.SECONDS);
        assertThat(finished).isTrue();

        assertThat(exceptions).isEmpty();
        assertThat(responses).hasSize(2);

        int q1 = responses.get(0).queueNumber();
        int q2 = responses.get(1).queueNumber();

        // Crucial concurrency invariant: Queue numbers must be strictly unique!
        assertThat(q1).isNotEqualTo(q2);
        assertThat(Set.of(q1, q2)).hasSize(2);
    }
}
