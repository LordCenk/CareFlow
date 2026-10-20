package com.careflow.billing.controller;

import com.careflow.billing.dto.InvoiceResponse;
import com.careflow.billing.dto.PaymentRecordResponse;
import com.careflow.billing.dto.ProcessPaymentRequest;
import com.careflow.billing.service.BillingService;
import com.careflow.common.response.ApiResponse;
import com.careflow.common.response.PagedResponse;
import com.careflow.patient.entity.Patient;
import com.careflow.patient.repository.PatientRepository;
import com.careflow.security.CustomUserPrincipal;
import com.careflow.user.entity.UserRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/invoices")
@Tag(name = "Billing & Invoices", description = "Consultation invoicing and simulated payment processing")
public class BillingController {

    private final BillingService billingService;
    private final PatientRepository patientRepository;

    public BillingController(BillingService billingService, PatientRepository patientRepository) {
        this.billingService = billingService;
        this.patientRepository = patientRepository;
    }

    @PostMapping("/generate/{appointmentId}")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'ADMIN')")
    @Operation(summary = "Generate an invoice for an appointment based on doctor consultation fee")
    public ResponseEntity<ApiResponse<InvoiceResponse>> generateInvoice(
            @PathVariable Long appointmentId,
            @AuthenticationPrincipal CustomUserPrincipal principal
    ) {
        InvoiceResponse response = billingService.generateInvoice(appointmentId, principal.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Invoice generated successfully", response));
    }

    @PostMapping("/{invoiceId}/payments")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'ADMIN', 'PATIENT')")
    @Operation(summary = "Process a simulated payment against an invoice")
    public ResponseEntity<ApiResponse<PaymentRecordResponse>> processPayment(
            @PathVariable Long invoiceId,
            @Valid @RequestBody ProcessPaymentRequest request,
            @AuthenticationPrincipal CustomUserPrincipal principal
    ) {
        PaymentRecordResponse response = billingService.processPayment(invoiceId, request, principal.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Payment processed successfully", response));
    }

    @PostMapping("/{invoiceId}/void")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Void an unpaid invoice (Admin only)")
    public ResponseEntity<ApiResponse<InvoiceResponse>> voidInvoice(
            @PathVariable Long invoiceId,
            @RequestParam(required = false, defaultValue = "Administrative void") String reason,
            @AuthenticationPrincipal CustomUserPrincipal principal
    ) {
        InvoiceResponse response = billingService.voidInvoice(invoiceId, reason, principal.getUsername());
        return ResponseEntity.ok(ApiResponse.success("Invoice voided successfully", response));
    }

    @GetMapping("/{invoiceId}")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'ADMIN', 'PATIENT')")
    @Operation(summary = "Get invoice details by ID")
    public ResponseEntity<ApiResponse<InvoiceResponse>> getInvoiceById(
            @PathVariable Long invoiceId,
            @AuthenticationPrincipal CustomUserPrincipal principal
    ) {
        InvoiceResponse response = billingService.getInvoiceById(invoiceId, principal);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'ADMIN', 'PATIENT')")
    @Operation(summary = "List invoices (filtered for patient or staff overview)")
    public ResponseEntity<ApiResponse<PagedResponse<InvoiceResponse>>> getInvoices(
            @RequestParam(required = false) Long patientId,
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal CustomUserPrincipal principal
    ) {
        PagedResponse<InvoiceResponse> response;

        if (principal.getRole() == UserRole.PATIENT) {
            Patient patient = patientRepository.findByUserId(principal.getId())
                    .orElseThrow(() -> new IllegalArgumentException("No patient profile linked to current user account"));
            response = billingService.getInvoicesForPatient(patient.getId(), pageable);
        } else if (patientId != null) {
            response = billingService.getInvoicesForPatient(patientId, pageable);
        } else {
            response = billingService.getAllInvoices(pageable);
        }

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{invoiceId}/payments")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'ADMIN', 'PATIENT')")
    @Operation(summary = "List payment transactions for an invoice")
    public ResponseEntity<ApiResponse<List<PaymentRecordResponse>>> getPaymentsForInvoice(@PathVariable Long invoiceId) {
        List<PaymentRecordResponse> payments = billingService.getPaymentsForInvoice(invoiceId);
        return ResponseEntity.ok(ApiResponse.success(payments));
    }
}
