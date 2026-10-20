package com.careflow.billing.dto;

import com.careflow.billing.entity.PaymentMethod;
import com.careflow.billing.entity.PaymentRecord;
import com.careflow.billing.entity.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record PaymentRecordResponse(
        Long id,
        Long invoiceId,
        String paymentReference,
        BigDecimal amount,
        PaymentMethod method,
        PaymentStatus status,
        Instant createdAt
) {
    public static PaymentRecordResponse fromEntity(PaymentRecord record) {
        return new PaymentRecordResponse(
                record.getId(),
                record.getInvoice() != null ? record.getInvoice().getId() : null,
                record.getPaymentReference(),
                record.getAmount(),
                record.getMethod(),
                record.getStatus(),
                record.getCreatedAt()
        );
    }
}
