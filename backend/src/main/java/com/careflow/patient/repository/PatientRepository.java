package com.careflow.patient.repository;

import com.careflow.patient.entity.Patient;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PatientRepository extends JpaRepository<Patient, Long> {
    Optional<Patient> findByUserId(Long userId);
    Optional<Patient> findByPatientReference(String patientReference);
    boolean existsByPatientReference(String patientReference);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Patient p WHERE p.id = :id")
    Optional<Patient> findByIdWithLock(@Param("id") Long id);
}
