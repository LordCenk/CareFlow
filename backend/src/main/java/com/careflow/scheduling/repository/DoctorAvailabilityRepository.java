package com.careflow.scheduling.repository;

import com.careflow.scheduling.entity.DoctorAvailability;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.DayOfWeek;
import java.util.List;

@Repository
public interface DoctorAvailabilityRepository extends JpaRepository<DoctorAvailability, Long> {
    List<DoctorAvailability> findByDoctorIdAndActiveTrue(Long doctorId);
    List<DoctorAvailability> findByDoctorIdAndDayOfWeekAndActiveTrue(Long doctorId, DayOfWeek dayOfWeek);
    List<DoctorAvailability> findByDoctorId(Long doctorId);
}
