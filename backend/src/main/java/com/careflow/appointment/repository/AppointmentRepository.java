package com.careflow.appointment.repository;

import com.careflow.appointment.entity.Appointment;
import com.careflow.appointment.entity.AppointmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    Optional<Appointment> findByAppointmentReference(String appointmentReference);

    @Query("SELECT a FROM Appointment a JOIN FETCH a.doctor d JOIN FETCH d.user JOIN FETCH d.department " +
            "JOIN FETCH a.patient p LEFT JOIN FETCH p.user WHERE a.id = :id")
    Optional<Appointment> findByIdWithDetails(@Param("id") Long id);

    @Query("SELECT a FROM Appointment a WHERE a.doctor.id = :doctorId " +
            "AND a.status IN :blockingStatuses " +
            "AND a.startDateTime < :endDateTime AND a.endDateTime > :startDateTime")
    List<Appointment> findConflictingDoctorAppointments(
            @Param("doctorId") Long doctorId,
            @Param("startDateTime") LocalDateTime startDateTime,
            @Param("endDateTime") LocalDateTime endDateTime,
            @Param("blockingStatuses") Collection<AppointmentStatus> blockingStatuses
    );

    @Query("SELECT a FROM Appointment a WHERE a.patient.id = :patientId " +
            "AND a.status IN :blockingStatuses " +
            "AND a.startDateTime < :endDateTime AND a.endDateTime > :startDateTime")
    List<Appointment> findConflictingPatientAppointments(
            @Param("patientId") Long patientId,
            @Param("startDateTime") LocalDateTime startDateTime,
            @Param("endDateTime") LocalDateTime endDateTime,
            @Param("blockingStatuses") Collection<AppointmentStatus> blockingStatuses
    );

    @Query("SELECT a FROM Appointment a WHERE a.doctor.id = :doctorId " +
            "AND a.startDateTime >= :dayStart AND a.startDateTime < :dayEnd " +
            "AND a.status IN :blockingStatuses")
    List<Appointment> findDoctorAppointmentsForDay(
            @Param("doctorId") Long doctorId,
            @Param("dayStart") LocalDateTime dayStart,
            @Param("dayEnd") LocalDateTime dayEnd,
            @Param("blockingStatuses") Collection<AppointmentStatus> blockingStatuses
    );

    Page<Appointment> findByPatientId(Long patientId, Pageable pageable);

    Page<Appointment> findByDoctorId(Long doctorId, Pageable pageable);

    @Query("SELECT a FROM Appointment a WHERE a.status = :status " +
            "AND a.startDateTime >= :startWindow AND a.startDateTime <= :endWindow")
    List<Appointment> findUpcomingAppointments(
            @Param("status") AppointmentStatus status,
            @Param("startWindow") LocalDateTime startWindow,
            @Param("endWindow") LocalDateTime endWindow
    );
}
