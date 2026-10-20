package com.careflow.department.controller;

import com.careflow.common.response.ApiResponse;
import com.careflow.department.dto.CreateDepartmentRequest;
import com.careflow.department.dto.DepartmentResponse;
import com.careflow.department.dto.UpdateDepartmentRequest;
import com.careflow.department.service.DepartmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/departments")
@Tag(name = "Departments", description = "Hospital clinical department management")
public class DepartmentController {

    private final DepartmentService departmentService;

    public DepartmentController(DepartmentService departmentService) {
        this.departmentService = departmentService;
    }

    @GetMapping
    @Operation(summary = "List departments (can filter active only)")
    public ResponseEntity<ApiResponse<List<DepartmentResponse>>> getDepartments(
            @RequestParam(required = false, defaultValue = "false") boolean activeOnly
    ) {
        List<DepartmentResponse> list = departmentService.getAllDepartments(activeOnly);
        return ResponseEntity.ok(ApiResponse.success(list));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get department by ID")
    public ResponseEntity<ApiResponse<DepartmentResponse>> getDepartmentById(@PathVariable Long id) {
        DepartmentResponse response = departmentService.getDepartmentById(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a new department (Admin only)")
    public ResponseEntity<ApiResponse<DepartmentResponse>> createDepartment(
            @Valid @RequestBody CreateDepartmentRequest request
    ) {
        DepartmentResponse response = departmentService.createDepartment(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Department created successfully", response));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update department details (Admin only)")
    public ResponseEntity<ApiResponse<DepartmentResponse>> updateDepartment(
            @PathVariable Long id,
            @Valid @RequestBody UpdateDepartmentRequest request
    ) {
        DepartmentResponse response = departmentService.updateDepartment(id, request);
        return ResponseEntity.ok(ApiResponse.success("Department updated successfully", response));
    }
}
