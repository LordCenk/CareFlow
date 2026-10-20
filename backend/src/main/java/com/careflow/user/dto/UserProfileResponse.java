package com.careflow.user.dto;

import com.careflow.user.entity.UserRole;
import java.time.Instant;

public record UserProfileResponse(
        Long id,
        String name,
        String email,
        UserRole role,
        boolean enabled,
        Long patientId,
        Long doctorId,
        Instant createdAt
) {}
