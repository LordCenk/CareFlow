package com.careflow.doctor.repository;

import com.careflow.doctor.entity.Doctor;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DoctorRepository extends JpaRepository<Doctor, Long> {

    Optional<Doctor> findByDoctorReference(String doctorReference);

    Optional<Doctor> findByUserId(Long userId);

    @Query("SELECT d FROM Doctor d JOIN FETCH d.department JOIN FETCH d.user WHERE d.id = :id")
    Optional<Doctor> findByIdWithDetails(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM Doctor d WHERE d.id = :id")
    Optional<Doctor> findByIdWithLock(@Param("id") Long id);

    @Query("SELECT d FROM Doctor d JOIN FETCH d.department JOIN FETCH d.user WHERE " +
            "(:departmentId IS NULL OR d.department.id = :departmentId) AND " +
            "(:specialization IS NULL OR LOWER(d.specialization) LIKE LOWER(CONCAT('%', :specialization, '%'))) AND " +
            "(:activeOnly = false OR d.active = true)")
    List<Doctor> searchDoctors(
            @Param("departmentId") Long departmentId,
            @Param("specialization") String specialization,
            @Param("activeOnly") boolean activeOnly
    );
}
