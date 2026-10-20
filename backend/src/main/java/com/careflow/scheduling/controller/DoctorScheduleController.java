package com.careflow.scheduling.controller;

import com.careflow.common.response.ApiResponse;
import com.careflow.scheduling.dto.*;
import com.careflow.scheduling.service.DoctorScheduleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@Tag(name = "Scheduling & Availability", description = "Doctor working hours, leave schedules, and slot availability")
public class DoctorScheduleController {

    private final DoctorScheduleService scheduleService;

    public DoctorScheduleController(DoctorScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    @GetMapping("/api/v1/doctors/{doctorId}/availability")
    @Operation(summary = "Get recurring weekly availability for a doctor")
    public ResponseEntity<ApiResponse<List<AvailabilityResponse>>> getDoctorAvailability(@PathVariable Long doctorId) {
        List<AvailabilityResponse> availability = scheduleService.getDoctorAvailability(doctorId);
        return ResponseEntity.ok(ApiResponse.success(availability));
    }

    @PostMapping("/api/v1/doctors/{doctorId}/availability")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Configure recurring working hours for a doctor (Admin only)")
    public ResponseEntity<ApiResponse<AvailabilityResponse>> addAvailability(
            @PathVariable Long doctorId,
            @Valid @RequestBody CreateAvailabilityRequest request
    ) {
        AvailabilityResponse response = scheduleService.addAvailability(doctorId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Availability configured successfully", response));
    }

    @GetMapping("/api/v1/doctors/{doctorId}/leaves")
    @Operation(summary = "Get leave periods for a doctor")
    public ResponseEntity<ApiResponse<List<LeaveResponse>>> getDoctorLeaves(@PathVariable Long doctorId) {
        List<LeaveResponse> leaves = scheduleService.getDoctorLeaves(doctorId);
        return ResponseEntity.ok(ApiResponse.success(leaves));
    }

    @PostMapping("/api/v1/doctors/{doctorId}/leaves")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR')")
    @Operation(summary = "Schedule a doctor leave/unavailability period")
    public ResponseEntity<ApiResponse<LeaveResponse>> addLeave(
            @PathVariable Long doctorId,
            @Valid @RequestBody CreateLeaveRequest request
    ) {
        LeaveResponse response = scheduleService.addLeave(doctorId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Leave scheduled successfully", response));
    }

    @GetMapping("/api/v1/appointments/available-slots")
    @Operation(summary = "Query real-time available appointment slots for a doctor on a specific date")
    public ResponseEntity<ApiResponse<List<AvailableSlotResponse>>> getAvailableSlots(
            @RequestParam Long doctorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        List<AvailableSlotResponse> slots = scheduleService.getAvailableSlots(doctorId, date);
        return ResponseEntity.ok(ApiResponse.success(slots));
    }
}
