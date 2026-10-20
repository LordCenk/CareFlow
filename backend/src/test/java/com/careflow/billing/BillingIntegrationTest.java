package com.careflow.billing;

import com.careflow.appointment.entity.Appointment;
import com.careflow.appointment.entity.AppointmentStatus;
import com.careflow.appointment.repository.AppointmentRepository;
import com.careflow.doctor.entity.Doctor;
import com.careflow.doctor.repository.DoctorRepository;
import com.careflow.patient.entity.Patient;
import com.careflow.patient.repository.PatientRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BillingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private DoctorRepository doctorRepository;

    @Autowired
    private PatientRepository patientRepository;

    private String receptionistToken;

    @BeforeEach
    void setUp() throws Exception {
        Map<String, String> recLogin = Map.of("email", "receptionist@careflow.com", "password", "password123");
        String recRes = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(recLogin)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        receptionistToken = objectMapper.readTree(recRes).path("data").path("token").asText();
    }

    @Test
    @DisplayName("End-to-End: Generate invoice for completed consultation, pay invoice, and verify PAID status")
    void shouldExecuteBillingAndPaymentWorkflow() throws Exception {
        Doctor doctor = doctorRepository.findById(1L).orElseThrow();
        Patient patient = patientRepository.findById(1L).orElseThrow();

        // Seed an appointment
        Appointment appt = new Appointment(
                "APT-BILL-" + System.currentTimeMillis(),
                patient,
                doctor,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().minusDays(1).plusMinutes(30),
                "Cardiology Consult",
                "receptionist"
        );
        appt.setStatus(AppointmentStatus.COMPLETED);
        Appointment savedAppt = appointmentRepository.save(appt);

        // 1. Generate invoice
        String invoiceRes = mockMvc.perform(post("/api/v1/invoices/generate/" + savedAppt.getId())
                        .header("Authorization", "Bearer " + receptionistToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.invoiceReference").isNotEmpty())
                .andExpect(jsonPath("$.data.amount").value(75.0))
                .andExpect(jsonPath("$.data.status").value("ISSUED"))
                .andReturn().getResponse().getContentAsString();

        Long invoiceId = objectMapper.readTree(invoiceRes).path("data").path("id").asLong();

        // 2. Process simulated payment
        Map<String, String> paymentPayload = Map.of("method", "CREDIT_CARD");
        mockMvc.perform(post("/api/v1/invoices/" + invoiceId + "/payments")
                        .header("Authorization", "Bearer " + receptionistToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(paymentPayload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.paymentReference").isNotEmpty())
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.amount").value(75.0));

        // 3. Verify invoice is now PAID
        mockMvc.perform(get("/api/v1/invoices/" + invoiceId)
                        .header("Authorization", "Bearer " + receptionistToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAID"))
                .andExpect(jsonPath("$.data.paidAt").isNotEmpty());

        // 4. Verify attempting duplicate payment on paid invoice fails
        mockMvc.perform(post("/api/v1/invoices/" + invoiceId + "/payments")
                        .header("Authorization", "Bearer " + receptionistToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(paymentPayload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("already paid")));
    }
}
