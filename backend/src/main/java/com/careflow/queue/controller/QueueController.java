package com.careflow.queue.controller;

import com.careflow.common.response.ApiResponse;
import com.careflow.queue.dto.CheckInRequest;
import com.careflow.queue.dto.DoctorQueueSummaryResponse;
import com.careflow.queue.dto.QueueEntryResponse;
import com.careflow.queue.service.QueueService;
import com.careflow.security.CustomUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/queue")
@Tag(name = "Queue Management", description = "Outpatient queue check-in, calling, and progression")
public class QueueController {

    private final QueueService queueService;

    public QueueController(QueueService queueService) {
        this.queueService = queueService;
    }

    @PostMapping("/check-in")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'ADMIN')")
    @Operation(summary = "Check in a patient for their appointment and allocate a queue number")
    public ResponseEntity<ApiResponse<QueueEntryResponse>> checkIn(
            @Valid @RequestBody CheckInRequest request,
            @AuthenticationPrincipal CustomUserPrincipal principal
    ) {
        QueueEntryResponse response = queueService.checkIn(request, principal.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Patient checked in successfully", response));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'DOCTOR', 'ADMIN')")
    @Operation(summary = "Get daily outpatient queue for a doctor and date")
    public ResponseEntity<ApiResponse<DoctorQueueSummaryResponse>> getQueue(
            @RequestParam Long doctorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        LocalDate queryDate = date != null ? date : LocalDate.now();
        DoctorQueueSummaryResponse response = queueService.getQueueForDoctorAndDate(doctorId, queryDate);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{queueEntryId}/call")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'DOCTOR', 'ADMIN')")
    @Operation(summary = "Call the next waiting patient in the queue")
    public ResponseEntity<ApiResponse<QueueEntryResponse>> callNext(
            @PathVariable Long queueEntryId,
            @AuthenticationPrincipal CustomUserPrincipal principal
    ) {
        QueueEntryResponse response = queueService.callNext(queueEntryId, principal.getUsername());
        return ResponseEntity.ok(ApiResponse.success("Patient called successfully", response));
    }

    @PostMapping("/{queueEntryId}/start-consultation")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    @Operation(summary = "Mark doctor consultation as started")
    public ResponseEntity<ApiResponse<QueueEntryResponse>> startConsultation(
            @PathVariable Long queueEntryId,
            @AuthenticationPrincipal CustomUserPrincipal principal
    ) {
        QueueEntryResponse response = queueService.startConsultation(queueEntryId, principal.getUsername());
        return ResponseEntity.ok(ApiResponse.success("Consultation started", response));
    }

    @PostMapping("/{queueEntryId}/complete")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    @Operation(summary = "Mark patient consultation as completed")
    public ResponseEntity<ApiResponse<QueueEntryResponse>> completeConsultation(
            @PathVariable Long queueEntryId,
            @AuthenticationPrincipal CustomUserPrincipal principal
    ) {
        QueueEntryResponse response = queueService.completeConsultation(queueEntryId, principal.getUsername());
        return ResponseEntity.ok(ApiResponse.success("Consultation completed", response));
    }

    @PostMapping("/{queueEntryId}/no-show")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'DOCTOR', 'ADMIN')")
    @Operation(summary = "Mark queue entry as no-show")
    public ResponseEntity<ApiResponse<QueueEntryResponse>> markNoShow(
            @PathVariable Long queueEntryId,
            @AuthenticationPrincipal CustomUserPrincipal principal
    ) {
        QueueEntryResponse response = queueService.markNoShow(queueEntryId, principal.getUsername());
        return ResponseEntity.ok(ApiResponse.success("Marked as no-show", response));
    }
}
