package com.careflow.scheduling.dto;

import com.careflow.scheduling.entity.DoctorAvailability;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

public record AvailabilityResponse(
        Long id,
        Long doctorId,
        DayOfWeek dayOfWeek,
        LocalTime startTime,
        LocalTime endTime,
        int slotDurationMinutes,
        LocalDate effectiveFrom,
        LocalDate effectiveUntil,
        boolean active
) {
    public static AvailabilityResponse fromEntity(DoctorAvailability availability) {
        return new AvailabilityResponse(
                availability.getId(),
                availability.getDoctor().getId(),
                availability.getDayOfWeek(),
                availability.getStartTime(),
                availability.getEndTime(),
                availability.getSlotDurationMinutes(),
                availability.getEffectiveFrom(),
                availability.getEffectiveUntil(),
                availability.isActive()
        );
    }
}
