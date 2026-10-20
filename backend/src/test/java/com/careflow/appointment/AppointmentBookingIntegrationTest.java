package com.careflow.appointment;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AppointmentBookingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String patientToken;
    private Long patientId;

    @BeforeEach
    void setUp() throws Exception {
        // Login as seeded patient Alice
        Map<String, String> loginPayload = Map.of(
                "email", "patient.alice@careflow.com",
                "password", "password123"
        );

        String loginResponse = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginPayload)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        patientToken = objectMapper.readTree(loginResponse).path("data").path("token").asText();
        patientId = objectMapper.readTree(loginResponse).path("data").path("patientId").asLong();
    }

    @Test
    @DisplayName("End-to-End: Book appointment, prevent duplicate slot booking, reschedule, and cancel")
    void shouldExecuteFullAppointmentLifecycle() throws Exception {
        // Dr. Smith (Doctor ID 1) works Mondays 09:00 - 13:00
        LocalDate nextMonday = LocalDate.now().plusWeeks(2).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
        LocalDateTime slotStart = nextMonday.atTime(9, 0);
        LocalDateTime slotEnd = nextMonday.atTime(9, 30);

        Map<String, Object> bookingPayload = Map.of(
                "patientId", patientId,
                "doctorId", 1L,
                "startDateTime", slotStart.toString(),
                "endDateTime", slotEnd.toString(),
                "reasonForVisit", "Routine cardiovascular screening"
        );

        // 1. Book appointment
        String bookingResponse = mockMvc.perform(post("/api/v1/appointments")
                        .header("Authorization", "Bearer " + patientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bookingPayload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("BOOKED"))
                .andExpect(jsonPath("$.data.appointmentReference").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        Long appointmentId = objectMapper.readTree(bookingResponse).path("data").path("id").asLong();

        // 2. Attempt to book the EXACT SAME slot again with another patient -> must be rejected with 409 CONFLICT
        // Login as Bob
        Map<String, String> bobLogin = Map.of("email", "patient.bob@careflow.com", "password", "password123");
        String bobRes = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bobLogin)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String bobToken = objectMapper.readTree(bobRes).path("data").path("token").asText();
        Long bobPatientId = objectMapper.readTree(bobRes).path("data").path("patientId").asLong();

        Map<String, Object> duplicatePayload = Map.of(
                "patientId", bobPatientId,
                "doctorId", 1L,
                "startDateTime", slotStart.toString(),
                "endDateTime", slotEnd.toString(),
                "reasonForVisit", "Heart examination"
        );

        mockMvc.perform(post("/api/v1/appointments")
                        .header("Authorization", "Bearer " + bobToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicatePayload)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message", containsString("already booked or overlaps")));

        // 3. Reschedule appointment to 10:00 - 10:30
        LocalDateTime rescheduledStart = nextMonday.atTime(10, 0);
        LocalDateTime rescheduledEnd = nextMonday.atTime(10, 30);
        Map<String, Object> reschedulePayload = Map.of(
                "newStartDateTime", rescheduledStart.toString(),
                "newEndDateTime", rescheduledEnd.toString(),
                "reason", "Schedule adjustment"
        );

        mockMvc.perform(post("/api/v1/appointments/" + appointmentId + "/reschedule")
                        .header("Authorization", "Bearer " + patientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reschedulePayload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.startDateTime", containsString("10:00")));

        // 4. Cancel appointment
        Map<String, String> cancelPayload = Map.of(
                "cancellationReason", "No longer required"
        );

        mockMvc.perform(post("/api/v1/appointments/" + appointmentId + "/cancel")
                        .header("Authorization", "Bearer " + patientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cancelPayload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }
}
