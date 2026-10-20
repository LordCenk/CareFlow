package com.careflow.scheduling.repository;

import com.careflow.scheduling.entity.DoctorLeave;
import com.careflow.scheduling.entity.LeaveStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface DoctorLeaveRepository extends JpaRepository<DoctorLeave, Long> {

    List<DoctorLeave> findByDoctorId(Long doctorId);

    List<DoctorLeave> findByDoctorIdAndStatus(Long doctorId, LeaveStatus status);

    @Query("SELECT dl FROM DoctorLeave dl WHERE dl.doctor.id = :doctorId AND dl.status = :status AND " +
            "dl.startDateTime < :endDateTime AND dl.endDateTime > :startDateTime")
    List<DoctorLeave> findOverlappingLeaves(
            @Param("doctorId") Long doctorId,
            @Param("startDateTime") LocalDateTime startDateTime,
            @Param("endDateTime") LocalDateTime endDateTime,
            @Param("status") LeaveStatus status
    );
}
