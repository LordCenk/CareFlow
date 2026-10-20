package com.careflow.user.dto;

import com.careflow.user.entity.UserRole;

public record AuthResponse(
        String token,
        String tokenType,
        Long userId,
        String name,
        String email,
        UserRole role,
        Long patientId,
        Long doctorId,
        long expiresInMs
) {}
