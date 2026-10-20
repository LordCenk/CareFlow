package com.careflow.billing.repository;

import com.careflow.billing.entity.Invoice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    Optional<Invoice> findByAppointmentId(Long appointmentId);

    Optional<Invoice> findByInvoiceReference(String invoiceReference);

    Page<Invoice> findByPatientId(Long patientId, Pageable pageable);

    @Query("SELECT i FROM Invoice i JOIN FETCH i.appointment a JOIN FETCH i.patient p LEFT JOIN FETCH p.user WHERE i.id = :id")
    Optional<Invoice> findByIdWithDetails(@Param("id") Long id);
}
