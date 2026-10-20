package com.careflow.doctor.service;

import com.careflow.common.exception.BusinessValidationException;
import com.careflow.common.exception.ResourceNotFoundException;
import com.careflow.department.entity.Department;
import com.careflow.department.repository.DepartmentRepository;
import com.careflow.doctor.dto.CreateDoctorRequest;
import com.careflow.doctor.dto.DoctorResponse;
import com.careflow.doctor.dto.UpdateDoctorRequest;
import com.careflow.doctor.entity.Doctor;
import com.careflow.doctor.repository.DoctorRepository;
import com.careflow.user.entity.User;
import com.careflow.user.entity.UserRole;
import com.careflow.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.util.List;
import java.util.UUID;

@Service
public class DoctorService {

    private final DoctorRepository doctorRepository;
    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;

    public DoctorService(
            DoctorRepository doctorRepository,
            DepartmentRepository departmentRepository,
            UserRepository userRepository
    ) {
        this.doctorRepository = doctorRepository;
        this.departmentRepository = departmentRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public DoctorResponse createDoctor(CreateDoctorRequest request) {
        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", request.userId()));

        if (user.getRole() != UserRole.DOCTOR) {
            throw new BusinessValidationException("Target user must have role DOCTOR to create a doctor profile");
        }

        if (doctorRepository.findByUserId(request.userId()).isPresent()) {
            throw new BusinessValidationException("Doctor profile already exists for this user account");
        }

        Department department = departmentRepository.findById(request.departmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Department", "id", request.departmentId()));

        if (!department.isActive()) {
            throw new BusinessValidationException("Cannot assign doctor to an inactive department");
        }

        String reference = generateDoctorReference();
        Doctor doctor = new Doctor(
                user,
                reference,
                department,
                request.specialization(),
                request.consultationFee()
        );

        Doctor saved = doctorRepository.save(doctor);
        return DoctorResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public DoctorResponse getDoctorById(Long id) {
        Doctor doctor = doctorRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor", "id", id));
        return DoctorResponse.fromEntity(doctor);
    }

    @Transactional(readOnly = true)
    public DoctorResponse getDoctorByUserId(Long userId) {
        Doctor doctor = doctorRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor profile not found for user id: " + userId));
        return DoctorResponse.fromEntity(doctor);
    }

    @Transactional(readOnly = true)
    public List<DoctorResponse> searchDoctors(Long departmentId, String specialization, boolean activeOnly) {
        List<Doctor> doctors = doctorRepository.searchDoctors(departmentId, specialization, activeOnly);
        return doctors.stream().map(DoctorResponse::fromEntity).toList();
    }

    @Transactional
    public DoctorResponse updateDoctor(Long id, UpdateDoctorRequest request) {
        Doctor doctor = doctorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor", "id", id));

        if (request.departmentId() != null) {
            Department department = departmentRepository.findById(request.departmentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Department", "id", request.departmentId()));
            doctor.setDepartment(department);
        }

        if (request.specialization() != null && !request.specialization().isBlank()) {
            doctor.setSpecialization(request.specialization());
        }

        if (request.consultationFee() != null) {
            doctor.setConsultationFee(request.consultationFee());
        }

        if (request.active() != null) {
            doctor.setActive(request.active());
        }

        Doctor updated = doctorRepository.save(doctor);
        return DoctorResponse.fromEntity(updated);
    }

    /**
     * Finds and acquires a pessimistic write lock on the doctor entity.
     * Crucial for concurrency control during slot reservation.
     */
    public Doctor getDoctorWithPessimisticLock(Long id) {
        return doctorRepository.findByIdWithLock(id)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor", "id", id));
    }

    public Doctor getDoctorEntity(Long id) {
        return doctorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor", "id", id));
    }

    private String generateDoctorReference() {
        int year = Year.now().getValue();
        String shortId = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return String.format("DOC-%d-%s", year, shortId);
    }
}
