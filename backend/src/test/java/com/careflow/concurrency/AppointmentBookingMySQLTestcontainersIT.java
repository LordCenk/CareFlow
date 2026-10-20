package com.careflow.concurrency;

import com.careflow.appointment.dto.BookAppointmentRequest;
import com.careflow.appointment.entity.Appointment;
import com.careflow.appointment.entity.AppointmentStatus;
import com.careflow.appointment.repository.AppointmentRepository;
import com.careflow.appointment.service.AppointmentService;
import com.careflow.common.exception.AppointmentConflictException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class AppointmentBookingMySQLTestcontainersIT {

    @Container
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4.0")
            .withDatabaseName("careflow_test")
            .withUsername("careflow")
            .withPassword("careflow");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        if (mysql.isRunning()) {
            registry.add("spring.datasource.url", mysql::getJdbcUrl);
            registry.add("spring.datasource.username", mysql::getUsername);
            registry.add("spring.datasource.password", mysql::getPassword);
            registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        }
    }

    @Autowired
    private AppointmentService appointmentService;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Test
    @DisplayName("Testcontainers MySQL 8.4: Concurrent booking attempts result in serialized isolation and 1 success")
    void shouldSerializeConcurrentBookingsInRealMySQL() throws Exception {
        Long doctorId = 1L;
        Long patient1Id = 1L;
        Long patient2Id = 2L;

        LocalDate futureMonday = LocalDate.now().plusWeeks(4).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
        LocalDateTime slotStart = futureMonday.atTime(9, 30);
        LocalDateTime slotEnd = futureMonday.atTime(10, 0);

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        List<Throwable> exceptions = Collections.synchronizedList(new ArrayList<>());

        executor.submit(() -> {
            readyLatch.countDown();
            try {
                startLatch.await();
                appointmentService.bookAppointment(
                        new BookAppointmentRequest(patient1Id, doctorId, slotStart, slotEnd, "Patient 1 test"),
                        "patient.alice@careflow.com"
                );
                successCount.incrementAndGet();
            } catch (AppointmentConflictException ex) {
                conflictCount.incrementAndGet();
            } catch (Throwable t) {
                exceptions.add(t);
            }
        });

        executor.submit(() -> {
            readyLatch.countDown();
            try {
                startLatch.await();
                appointmentService.bookAppointment(
                        new BookAppointmentRequest(patient2Id, doctorId, slotStart, slotEnd, "Patient 2 test"),
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
        startLatch.countDown();

        executor.shutdown();
        executor.awaitTermination(15, TimeUnit.SECONDS);

        assertThat(exceptions).isEmpty();
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(1);

        List<Appointment> bookedAppointments = appointmentRepository.findConflictingDoctorAppointments(
                doctorId, slotStart, slotEnd, List.of(AppointmentStatus.BOOKED)
        );
        assertThat(bookedAppointments).hasSize(1);
    }
}
