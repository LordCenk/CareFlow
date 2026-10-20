package com.careflow.queue;

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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class QueueIntegrationTest {

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
    private String doctorToken;

    @BeforeEach
    void setUp() throws Exception {
        // Login as receptionist
        Map<String, String> recLogin = Map.of("email", "receptionist@careflow.com", "password", "password123");
        String recRes = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(recLogin)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        receptionistToken = objectMapper.readTree(recRes).path("data").path("token").asText();

        // Login as Doctor Smith
        Map<String, String> docLogin = Map.of("email", "dr.smith@careflow.com", "password", "password123");
        String docRes = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(docLogin)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        doctorToken = objectMapper.readTree(docRes).path("data").path("token").asText();
    }

    @Test
    @DisplayName("End-to-End: Check in today's appointment, view doctor queue, call next, and complete consultation")
    void shouldExecuteFullQueueProgressionWorkflow() throws Exception {
        Doctor doctor = doctorRepository.findById(1L).orElseThrow();
        Patient patient = patientRepository.findById(1L).orElseThrow();

        // Seed an appointment for today
        Appointment todayAppt = new Appointment(
                "APT-QUEUE-" + System.currentTimeMillis(),
                patient,
                doctor,
                LocalDate.now().atTime(11, 0),
                LocalDate.now().atTime(11, 30),
                "Consultation",
                "receptionist"
        );
        todayAppt.setStatus(AppointmentStatus.BOOKED);
        Appointment savedAppt = appointmentRepository.save(todayAppt);

        // 1. Receptionist checks in patient
        Map<String, Long> checkInPayload = Map.of("appointmentId", savedAppt.getId());
        String checkInRes = mockMvc.perform(post("/api/v1/queue/check-in")
                        .header("Authorization", "Bearer " + receptionistToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(checkInPayload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.queueNumber").isNumber())
                .andExpect(jsonPath("$.data.status").value("WAITING"))
                .andReturn().getResponse().getContentAsString();

        Long queueEntryId = objectMapper.readTree(checkInRes).path("data").path("id").asLong();

        // 2. View doctor's queue overview
        mockMvc.perform(get("/api/v1/queue?doctorId=1")
                        .header("Authorization", "Bearer " + receptionistToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.doctorId").value(1))
                .andExpect(jsonPath("$.data.totalCheckedIn").isNumber());

        // 3. Call patient
        mockMvc.perform(post("/api/v1/queue/" + queueEntryId + "/call")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CALLED"))
                .andExpect(jsonPath("$.data.calledAt").isNotEmpty());

        // 4. Start consultation
        mockMvc.perform(post("/api/v1/queue/" + queueEntryId + "/start-consultation")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_CONSULTATION"));

        // 5. Complete consultation
        mockMvc.perform(post("/api/v1/queue/" + queueEntryId + "/complete")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.completedAt").isNotEmpty());
    }
}
