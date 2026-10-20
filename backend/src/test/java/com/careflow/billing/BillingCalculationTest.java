package com.careflow.billing;

import com.careflow.appointment.entity.Appointment;
import com.careflow.appointment.repository.AppointmentRepository;
import com.careflow.audit.service.AuditService;
import com.careflow.billing.dto.ProcessPaymentRequest;
import com.careflow.billing.entity.Invoice;
import com.careflow.billing.entity.InvoiceStatus;
import com.careflow.billing.entity.PaymentMethod;
import com.careflow.billing.entity.PaymentRecord;
import com.careflow.billing.repository.InvoiceRepository;
import com.careflow.billing.repository.PaymentRecordRepository;
import com.careflow.billing.service.BillingService;
import com.careflow.common.exception.BusinessValidationException;
import com.careflow.doctor.entity.Doctor;
import com.careflow.patient.entity.Patient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BillingCalculationTest {

    @Mock
    private InvoiceRepository invoiceRepository;
    @Mock
    private PaymentRecordRepository paymentRecordRepository;
    @Mock
    private AppointmentRepository appointmentRepository;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private BillingService billingService;

    private Appointment sampleAppointment;
    private Doctor sampleDoctor;
    private Patient samplePatient;

    @BeforeEach
    void setUp() {
        sampleDoctor = new Doctor();
        sampleDoctor.setId(1L);
        sampleDoctor.setConsultationFee(BigDecimal.valueOf(85.50));

        samplePatient = new Patient();
        samplePatient.setId(2L);

        sampleAppointment = new Appointment();
        sampleAppointment.setId(10L);
        sampleAppointment.setDoctor(sampleDoctor);
        sampleAppointment.setPatient(samplePatient);
        sampleAppointment.setAppointmentReference("APT-BILL-01");
    }

    @Test
    @DisplayName("Should derive invoice amount strictly from doctor's consultation fee")
    void shouldDeriveInvoiceAmountFromDoctorFee() {
        when(appointmentRepository.findById(10L)).thenReturn(Optional.of(sampleAppointment));
        when(invoiceRepository.findByAppointmentId(10L)).thenReturn(Optional.empty());
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(i -> {
            Invoice inv = i.getArgument(0);
            inv.setId(500L);
            return inv;
        });

        var invoiceRes = billingService.generateInvoice(10L, "receptionist");

        assertThat(invoiceRes.amount()).isEqualByComparingTo(BigDecimal.valueOf(85.50));
        assertThat(invoiceRes.status()).isEqualTo(InvoiceStatus.ISSUED);
    }

    @Test
    @DisplayName("Should prevent duplicate payments on an already paid invoice")
    void shouldPreventDuplicatePaymentOnPaidInvoice() {
        Invoice paidInvoice = new Invoice("INV-001", sampleAppointment, samplePatient, BigDecimal.valueOf(85.50), "USD");
        paidInvoice.setId(500L);
        paidInvoice.setStatus(InvoiceStatus.PAID);

        when(invoiceRepository.findById(500L)).thenReturn(Optional.of(paidInvoice));

        assertThatThrownBy(() -> billingService.processPayment(500L, new ProcessPaymentRequest(PaymentMethod.CREDIT_CARD), "patient"))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessageContaining("is already paid");
    }

    @Test
    @DisplayName("Should transition invoice to PAID upon successful simulated payment")
    void shouldTransitionInvoiceToPaidUponPayment() {
        Invoice issuedInvoice = new Invoice("INV-002", sampleAppointment, samplePatient, BigDecimal.valueOf(85.50), "USD");
        issuedInvoice.setId(501L);
        issuedInvoice.setStatus(InvoiceStatus.ISSUED);

        when(invoiceRepository.findById(501L)).thenReturn(Optional.of(issuedInvoice));
        when(paymentRecordRepository.save(any(PaymentRecord.class))).thenAnswer(i -> {
            PaymentRecord p = i.getArgument(0);
            p.setId(900L);
            return p;
        });
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(i -> i.getArgument(0));

        var paymentRes = billingService.processPayment(501L, new ProcessPaymentRequest(PaymentMethod.SIMULATED_ONLINE), "patient");

        assertThat(paymentRes.amount()).isEqualByComparingTo(BigDecimal.valueOf(85.50));
        assertThat(issuedInvoice.getStatus()).isEqualTo(InvoiceStatus.PAID);
        assertThat(issuedInvoice.getPaidAt()).isNotNull();
    }
}
