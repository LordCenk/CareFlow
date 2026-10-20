package com.careflow.doctor.controller;

import com.careflow.common.response.ApiResponse;
import com.careflow.doctor.dto.CreateDoctorRequest;
import com.careflow.doctor.dto.DoctorResponse;
import com.careflow.doctor.dto.UpdateDoctorRequest;
import com.careflow.doctor.service.DoctorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/doctors")
@Tag(name = "Doctors", description = "Doctor profile and consultation settings")
public class DoctorController {

    private final DoctorService doctorService;

    public DoctorController(DoctorService doctorService) {
        this.doctorService = doctorService;
    }

    @GetMapping
    @Operation(summary = "Search active doctors by department or specialization")
    public ResponseEntity<ApiResponse<List<DoctorResponse>>> searchDoctors(
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) String specialization,
            @RequestParam(required = false, defaultValue = "true") boolean activeOnly
    ) {
        List<DoctorResponse> doctors = doctorService.searchDoctors(departmentId, specialization, activeOnly);
        return ResponseEntity.ok(ApiResponse.success(doctors));
    }

    @GetMapping("/{doctorId}")
    @Operation(summary = "Get doctor details by ID")
    public ResponseEntity<ApiResponse<DoctorResponse>> getDoctorById(@PathVariable Long doctorId) {
        DoctorResponse response = doctorService.getDoctorById(doctorId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Register a new doctor profile (Admin only)")
    public ResponseEntity<ApiResponse<DoctorResponse>> createDoctor(
            @Valid @RequestBody CreateDoctorRequest request
    ) {
        DoctorResponse response = doctorService.createDoctor(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Doctor created successfully", response));
    }

    @PutMapping("/{doctorId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update doctor details (Admin only)")
    public ResponseEntity<ApiResponse<DoctorResponse>> updateDoctor(
            @PathVariable Long doctorId,
            @Valid @RequestBody UpdateDoctorRequest request
    ) {
        DoctorResponse response = doctorService.updateDoctor(doctorId, request);
        return ResponseEntity.ok(ApiResponse.success("Doctor updated successfully", response));
    }
}
