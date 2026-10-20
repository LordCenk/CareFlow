package com.careflow.scheduling.dto;

import com.careflow.scheduling.entity.DoctorLeave;
import com.careflow.scheduling.entity.LeaveStatus;
import java.time.Instant;
import java.time.LocalDateTime;

public record LeaveResponse(
        Long id,
        Long doctorId,
        LocalDateTime startDateTime,
        LocalDateTime endDateTime,
        String reason,
        LeaveStatus status,
        Instant createdAt
) {
    public static LeaveResponse fromEntity(DoctorLeave leave) {
        return new LeaveResponse(
                leave.getId(),
                leave.getDoctor().getId(),
                leave.getStartDateTime(),
                leave.getEndDateTime(),
                leave.getReason(),
                leave.getStatus(),
                leave.getCreatedAt()
        );
    }
}
