package com.careflow.department.service;

import com.careflow.common.exception.DuplicateResourceException;
import com.careflow.common.exception.ResourceNotFoundException;
import com.careflow.department.dto.CreateDepartmentRequest;
import com.careflow.department.dto.DepartmentResponse;
import com.careflow.department.dto.UpdateDepartmentRequest;
import com.careflow.department.entity.Department;
import com.careflow.department.repository.DepartmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DepartmentService {

    private final DepartmentRepository departmentRepository;

    public DepartmentService(DepartmentRepository departmentRepository) {
        this.departmentRepository = departmentRepository;
    }

    @Transactional
    public DepartmentResponse createDepartment(CreateDepartmentRequest request) {
        if (departmentRepository.existsByName(request.name())) {
            throw new DuplicateResourceException("Department with name '" + request.name() + "' already exists");
        }

        Department department = new Department(request.name(), request.description());
        Department saved = departmentRepository.save(department);
        return DepartmentResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public List<DepartmentResponse> getAllDepartments(boolean activeOnly) {
        List<Department> list = activeOnly
                ? departmentRepository.findByActiveTrue()
                : departmentRepository.findAll();
        return list.stream().map(DepartmentResponse::fromEntity).toList();
    }

    @Transactional(readOnly = true)
    public DepartmentResponse getDepartmentById(Long id) {
        Department department = departmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Department", "id", id));
        return DepartmentResponse.fromEntity(department);
    }

    @Transactional
    public DepartmentResponse updateDepartment(Long id, UpdateDepartmentRequest request) {
        Department department = departmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Department", "id", id));

        if (!department.getName().equalsIgnoreCase(request.name()) && departmentRepository.existsByName(request.name())) {
            throw new DuplicateResourceException("Department with name '" + request.name() + "' already exists");
        }

        department.setName(request.name());
        department.setDescription(request.description());
        if (request.active() != null) {
            department.setActive(request.active());
        }

        Department updated = departmentRepository.save(department);
        return DepartmentResponse.fromEntity(updated);
    }

    public Department getDepartmentEntity(Long id) {
        return departmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Department", "id", id));
    }
}
