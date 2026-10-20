package com.careflow.appointment.controller;

import com.careflow.appointment.dto.*;
import com.careflow.appointment.entity.AppointmentStatus;
import com.careflow.appointment.service.AppointmentService;
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

@RestController
@RequestMapping("/api/v1/appointments")
@Tag(name = "Appointments", description = "Appointment booking, scheduling, state changes, and cancellation")
public class AppointmentController {

    private final AppointmentService appointmentService;
    private final PatientRepository patientRepository;

    public AppointmentController(AppointmentService appointmentService, PatientRepository patientRepository) {
        this.appointmentService = appointmentService;
        this.patientRepository = patientRepository;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('PATIENT', 'RECEPTIONIST', 'ADMIN')")
    @Operation(summary = "Book a new appointment slot")
    public ResponseEntity<ApiResponse<AppointmentResponse>> bookAppointment(
            @Valid @RequestBody BookAppointmentRequest request,
            @AuthenticationPrincipal CustomUserPrincipal principal
    ) {
        // If patient books, verify they are booking for their own profile
        if (principal.getRole() == UserRole.PATIENT) {
            Patient patient = patientRepository.findByUserId(principal.getId())
                    .orElseThrow(() -> new IllegalArgumentException("No patient profile linked to current user account"));
            if (!patient.getId().equals(request.patientId())) {
                throw new org.springframework.security.access.AccessDeniedException("Patients can only book appointments for themselves");
            }
        }

        AppointmentResponse response = appointmentService.bookAppointment(request, principal.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Appointment booked successfully", response));
    }

    @GetMapping("/{appointmentId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST', 'DOCTOR', 'PATIENT')")
    @Operation(summary = "Get appointment details by ID")
    public ResponseEntity<ApiResponse<AppointmentResponse>> getAppointmentById(
            @PathVariable Long appointmentId,
            @AuthenticationPrincipal CustomUserPrincipal principal
    ) {
        AppointmentResponse response = appointmentService.getAppointmentById(appointmentId, principal);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST', 'DOCTOR', 'PATIENT')")
    @Operation(summary = "List appointments (paginated, filtered by context or query params)")
    public ResponseEntity<ApiResponse<PagedResponse<AppointmentResponse>>> getAppointments(
            @RequestParam(required = false) Long patientId,
            @RequestParam(required = false) Long doctorId,
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal CustomUserPrincipal principal
    ) {
        PagedResponse<AppointmentResponse> response;

        if (principal.getRole() == UserRole.PATIENT) {
            Patient patient = patientRepository.findByUserId(principal.getId())
                    .orElseThrow(() -> new IllegalArgumentException("No patient profile linked to current user account"));
            response = appointmentService.getAppointmentsForPatient(patient.getId(), pageable);
        } else if (patientId != null) {
            response = appointmentService.getAppointmentsForPatient(patientId, pageable);
        } else if (doctorId != null) {
            response = appointmentService.getAppointmentsForDoctor(doctorId, pageable);
        } else {
            response = appointmentService.getAllAppointments(pageable);
        }

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{appointmentId}/reschedule")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST', 'PATIENT')")
    @Operation(summary = "Reschedule an existing booked appointment")
    public ResponseEntity<ApiResponse<AppointmentResponse>> rescheduleAppointment(
            @PathVariable Long appointmentId,
            @Valid @RequestBody RescheduleAppointmentRequest request,
            @AuthenticationPrincipal CustomUserPrincipal principal
    ) {
        AppointmentResponse response = appointmentService.rescheduleAppointment(appointmentId, request, principal.getUsername());
        return ResponseEntity.ok(ApiResponse.success("Appointment rescheduled successfully", response));
    }

    @PostMapping("/{appointmentId}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST', 'DOCTOR', 'PATIENT')")
    @Operation(summary = "Cancel an appointment with a cancellation reason")
    public ResponseEntity<ApiResponse<AppointmentResponse>> cancelAppointment(
            @PathVariable Long appointmentId,
            @Valid @RequestBody CancelAppointmentRequest request,
            @AuthenticationPrincipal CustomUserPrincipal principal
    ) {
        AppointmentResponse response = appointmentService.cancelAppointment(appointmentId, request, principal);
        return ResponseEntity.ok(ApiResponse.success("Appointment cancelled successfully", response));
    }

    @PatchMapping("/{appointmentId}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST', 'DOCTOR')")
    @Operation(summary = "Update appointment status via domain state machine")
    public ResponseEntity<ApiResponse<AppointmentResponse>> updateStatus(
            @PathVariable Long appointmentId,
            @Valid @RequestBody UpdateAppointmentStatusRequest request,
            @AuthenticationPrincipal CustomUserPrincipal principal
    ) {
        AppointmentResponse response = appointmentService.updateStatus(appointmentId, request.status(), principal);
        return ResponseEntity.ok(ApiResponse.success("Appointment status updated successfully", response));
    }
}
