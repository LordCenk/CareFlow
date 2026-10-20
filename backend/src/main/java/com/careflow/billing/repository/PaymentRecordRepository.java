package com.careflow.billing.repository;

import com.careflow.billing.entity.PaymentRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PaymentRecordRepository extends JpaRepository<PaymentRecord, Long> {
    List<PaymentRecord> findByInvoiceId(Long invoiceId);
}
