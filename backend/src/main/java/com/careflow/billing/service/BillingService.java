package com.careflow.billing.service;

import com.careflow.appointment.entity.Appointment;
import com.careflow.appointment.repository.AppointmentRepository;
import com.careflow.audit.service.AuditService;
import com.careflow.billing.dto.InvoiceResponse;
import com.careflow.billing.dto.PaymentRecordResponse;
import com.careflow.billing.dto.ProcessPaymentRequest;
import com.careflow.billing.entity.Invoice;
import com.careflow.billing.entity.InvoiceStatus;
import com.careflow.billing.entity.PaymentRecord;
import com.careflow.billing.entity.PaymentStatus;
import com.careflow.billing.repository.InvoiceRepository;
import com.careflow.billing.repository.PaymentRecordRepository;
import com.careflow.common.exception.BusinessValidationException;
import com.careflow.common.exception.DuplicateResourceException;
import com.careflow.common.exception.ResourceNotFoundException;
import com.careflow.common.response.PagedResponse;
import com.careflow.security.CustomUserPrincipal;
import com.careflow.user.entity.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.Year;
import java.util.List;
import java.util.UUID;

@Service
public class BillingService {

    private final InvoiceRepository invoiceRepository;
    private final PaymentRecordRepository paymentRecordRepository;
    private final AppointmentRepository appointmentRepository;
    private final AuditService auditService;

    public BillingService(
            InvoiceRepository invoiceRepository,
            PaymentRecordRepository paymentRecordRepository,
            AppointmentRepository appointmentRepository,
            AuditService auditService
    ) {
        this.invoiceRepository = invoiceRepository;
        this.paymentRecordRepository = paymentRecordRepository;
        this.appointmentRepository = appointmentRepository;
        this.auditService = auditService;
    }

    /**
     * Generates an invoice for an appointment, deriving the fee strictly from server-side doctor data.
     */
    @Transactional
    public InvoiceResponse generateInvoice(Long appointmentId, String performedBy) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", "id", appointmentId));

        if (invoiceRepository.findByAppointmentId(appointmentId).isPresent()) {
            throw new DuplicateResourceException("An invoice already exists for appointment " + appointment.getAppointmentReference());
        }

        BigDecimal fee = appointment.getDoctor().getConsultationFee();
        String reference = generateInvoiceReference();

        Invoice invoice = new Invoice(
                reference,
                appointment,
                appointment.getPatient(),
                fee,
                "USD"
        );

        Invoice saved = invoiceRepository.save(invoice);
        auditService.recordAudit("INVOICE", saved.getId(), "GENERATED", performedBy,
                "Generated invoice " + reference + " for amount $" + fee);

        return InvoiceResponse.fromEntity(saved);
    }

    /**
     * Records a simulated payment and transitions invoice to PAID.
     */
    @Transactional
    public PaymentRecordResponse processPayment(Long invoiceId, ProcessPaymentRequest request, String performedBy) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", "id", invoiceId));

        if (invoice.getStatus() == InvoiceStatus.PAID) {
            throw new BusinessValidationException("Invoice " + invoice.getInvoiceReference() + " is already paid");
        }

        if (invoice.getStatus() == InvoiceStatus.VOID) {
            throw new BusinessValidationException("Cannot make payments toward a voided invoice");
        }

        String paymentRef = generatePaymentReference();
        PaymentRecord paymentRecord = new PaymentRecord(
                invoice,
                paymentRef,
                invoice.getAmount(),
                request.method(),
                PaymentStatus.SUCCESS
        );

        PaymentRecord savedPayment = paymentRecordRepository.save(paymentRecord);

        invoice.setStatus(InvoiceStatus.PAID);
        invoice.setPaidAt(Instant.now());
        invoiceRepository.save(invoice);

        auditService.recordAudit("PAYMENT", savedPayment.getId(), "PROCESSED", performedBy,
                "Processed simulated payment " + paymentRef + " of $" + invoice.getAmount() + " via " + request.method());

        return PaymentRecordResponse.fromEntity(savedPayment);
    }

    /**
     * Voids an unpaid invoice.
     */
    @Transactional
    public InvoiceResponse voidInvoice(Long invoiceId, String reason, String performedBy) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", "id", invoiceId));

        if (invoice.getStatus() == InvoiceStatus.PAID) {
            throw new BusinessValidationException("Paid invoices cannot be voided");
        }

        invoice.setStatus(InvoiceStatus.VOID);
        Invoice updated = invoiceRepository.save(invoice);

        auditService.recordAudit("INVOICE", updated.getId(), "VOIDED", performedBy,
                "Voided invoice. Reason: " + reason);

        return InvoiceResponse.fromEntity(updated);
    }

    @Transactional(readOnly = true)
    public InvoiceResponse getInvoiceById(Long id, CustomUserPrincipal principal) {
        Invoice invoice = invoiceRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", "id", id));

        if (principal.getRole() == UserRole.PATIENT) {
            if (invoice.getPatient().getUser() == null || !invoice.getPatient().getUser().getId().equals(principal.getId())) {
                throw new AccessDeniedException("You do not have permission to view this invoice");
            }
        }

        return InvoiceResponse.fromEntity(invoice);
    }

    @Transactional(readOnly = true)
    public PagedResponse<InvoiceResponse> getInvoicesForPatient(Long patientId, Pageable pageable) {
        Page<InvoiceResponse> page = invoiceRepository.findByPatientId(patientId, pageable)
                .map(InvoiceResponse::fromEntity);
        return PagedResponse.from(page);
    }

    @Transactional(readOnly = true)
    public PagedResponse<InvoiceResponse> getAllInvoices(Pageable pageable) {
        Page<InvoiceResponse> page = invoiceRepository.findAll(pageable)
                .map(InvoiceResponse::fromEntity);
        return PagedResponse.from(page);
    }

    @Transactional(readOnly = true)
    public List<PaymentRecordResponse> getPaymentsForInvoice(Long invoiceId) {
        if (!invoiceRepository.existsById(invoiceId)) {
            throw new ResourceNotFoundException("Invoice", "id", invoiceId);
        }
        return paymentRecordRepository.findByInvoiceId(invoiceId)
                .stream()
                .map(PaymentRecordResponse::fromEntity)
                .toList();
    }

    private String generateInvoiceReference() {
        int year = Year.now().getValue();
        String shortUuid = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return String.format("INV-%d-%s", year, shortUuid);
    }

    private String generatePaymentReference() {
        int year = Year.now().getValue();
        String shortUuid = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return String.format("PAY-%d-%s", year, shortUuid);
    }
}
