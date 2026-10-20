package com.careflow.billing.dto;

import com.careflow.billing.entity.Invoice;
import com.careflow.billing.entity.InvoiceStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record InvoiceResponse(
        Long id,
        String invoiceReference,
        Long appointmentId,
        String appointmentReference,
        Long patientId,
        String patientName,
        BigDecimal amount,
        String currency,
        InvoiceStatus status,
        Instant issuedAt,
        Instant paidAt
) {
    public static InvoiceResponse fromEntity(Invoice invoice) {
        String patientName = "Unknown Patient";
        if (invoice.getPatient() != null && invoice.getPatient().getUser() != null) {
            patientName = invoice.getPatient().getUser().getName();
        }

        String apptRef = invoice.getAppointment() != null ? invoice.getAppointment().getAppointmentReference() : null;

        return new InvoiceResponse(
                invoice.getId(),
                invoice.getInvoiceReference(),
                invoice.getAppointment() != null ? invoice.getAppointment().getId() : null,
                apptRef,
                invoice.getPatient() != null ? invoice.getPatient().getId() : null,
                patientName,
                invoice.getAmount(),
                invoice.getCurrency(),
                invoice.getStatus(),
                invoice.getIssuedAt(),
                invoice.getPaidAt()
        );
    }
}
