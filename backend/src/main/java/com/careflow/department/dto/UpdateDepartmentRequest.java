package com.careflow.department.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateDepartmentRequest(
        @NotBlank(message = "Department name is required")
        @Size(max = 100, message = "Name must not exceed 100 characters")
        String name,

        @Size(max = 500, message = "Description must not exceed 500 characters")
        String description,

        Boolean active
) {}
