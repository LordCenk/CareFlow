package com.careflow.queue.dto;

import java.time.LocalDate;
import java.util.List;

public record DoctorQueueSummaryResponse(
        Long doctorId,
        String doctorName,
        LocalDate queueDate,
        int totalCheckedIn,
        int waitingCount,
        int inConsultationCount,
        int completedCount,
        List<QueueEntryResponse> entries
) {}
