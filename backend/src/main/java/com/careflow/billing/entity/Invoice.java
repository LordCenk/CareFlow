package com.careflow.billing.entity;

import com.careflow.appointment.entity.Appointment;
import com.careflow.patient.entity.Patient;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "invoices")
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "invoice_reference", nullable = false, unique = true, length = 40)
    private String invoiceReference;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appointment_id", nullable = false, unique = true)
    private Appointment appointment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 10)
    private String currency = "USD";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InvoiceStatus status = InvoiceStatus.ISSUED;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Invoice() {
    }

    public Invoice(String invoiceReference, Appointment appointment, Patient patient, BigDecimal amount, String currency) {
        this.invoiceReference = invoiceReference;
        this.appointment = appointment;
        this.patient = patient;
        this.amount = amount;
        this.currency = currency != null ? currency : "USD";
        this.status = InvoiceStatus.ISSUED;
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.issuedAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getInvoiceReference() {
        return invoiceReference;
    }

    public void setInvoiceReference(String invoiceReference) {
        this.invoiceReference = invoiceReference;
    }

    public Appointment getAppointment() {
        return appointment;
    }

    public void setAppointment(Appointment appointment) {
        this.appointment = appointment;
    }

    public Patient getPatient() {
        return patient;
    }

    public void setPatient(Patient patient) {
        this.patient = patient;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public InvoiceStatus getStatus() {
        return status;
    }

    public void setStatus(InvoiceStatus status) {
        this.status = status;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(Instant paidAt) {
        this.paidAt = paidAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
