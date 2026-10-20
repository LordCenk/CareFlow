package com.careflow.patient.controller;

import com.careflow.common.response.ApiResponse;
import com.careflow.common.response.PagedResponse;
import com.careflow.patient.dto.CreatePatientRequest;
import com.careflow.patient.dto.PatientResponse;
import com.careflow.patient.dto.UpdatePatientRequest;
import com.careflow.patient.service.PatientService;
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
@RequestMapping("/api/v1/patients")
@Tag(name = "Patients", description = "Patient registration and profile management")
public class PatientController {

    private final PatientService patientService;

    public PatientController(PatientService patientService) {
        this.patientService = patientService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    @Operation(summary = "Register a new patient record (Staff)")
    public ResponseEntity<ApiResponse<PatientResponse>> createPatient(
            @Valid @RequestBody CreatePatientRequest request
    ) {
        PatientResponse response = patientService.createPatient(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Patient created successfully", response));
    }

    @GetMapping("/{patientId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST', 'DOCTOR', 'PATIENT')")
    @Operation(summary = "Get patient profile by ID")
    public ResponseEntity<ApiResponse<PatientResponse>> getPatientById(
            @PathVariable Long patientId,
            @AuthenticationPrincipal CustomUserPrincipal principal
    ) {
        boolean isStaff = principal.getRole() != UserRole.PATIENT;
        PatientResponse response = patientService.getPatientById(patientId, principal.getId(), isStaff);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/{patientId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST', 'PATIENT')")
    @Operation(summary = "Update patient contact information")
    public ResponseEntity<ApiResponse<PatientResponse>> updatePatient(
            @PathVariable Long patientId,
            @Valid @RequestBody UpdatePatientRequest request,
            @AuthenticationPrincipal CustomUserPrincipal principal
    ) {
        boolean isStaff = principal.getRole() != UserRole.PATIENT;
        PatientResponse response = patientService.updatePatient(patientId, request, principal.getId(), isStaff);
        return ResponseEntity.ok(ApiResponse.success("Patient updated successfully", response));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    @Operation(summary = "List all patients with pagination (Staff)")
    public ResponseEntity<ApiResponse<PagedResponse<PatientResponse>>> getAllPatients(
            @PageableDefault(size = 20) Pageable pageable
    ) {
        PagedResponse<PatientResponse> response = patientService.getAllPatients(pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
