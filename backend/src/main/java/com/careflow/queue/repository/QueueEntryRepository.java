package com.careflow.queue.repository;

import com.careflow.queue.entity.QueueEntry;
import com.careflow.queue.entity.QueueStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface QueueEntryRepository extends JpaRepository<QueueEntry, Long> {

    Optional<QueueEntry> findByAppointmentId(Long appointmentId);

    boolean existsByAppointmentId(Long appointmentId);

    @Query("SELECT q FROM QueueEntry q JOIN FETCH q.appointment a JOIN FETCH a.patient p LEFT JOIN FETCH p.user " +
            "JOIN FETCH q.doctor d JOIN FETCH d.user " +
            "WHERE q.doctor.id = :doctorId AND q.queueDate = :queueDate " +
            "ORDER BY q.queueNumber ASC")
    List<QueueEntry> findByDoctorAndDate(@Param("doctorId") Long doctorId, @Param("queueDate") LocalDate queueDate);

    @Query("SELECT q FROM QueueEntry q WHERE q.doctor.id = :doctorId AND q.queueDate = :queueDate " +
            "AND q.status = :status ORDER BY q.queueNumber ASC")
    List<QueueEntry> findByDoctorAndDateAndStatus(
            @Param("doctorId") Long doctorId,
            @Param("queueDate") LocalDate queueDate,
            @Param("status") QueueStatus status
    );
}
