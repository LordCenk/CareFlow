package com.careflow.patient.service;

import com.careflow.common.exception.BusinessValidationException;
import com.careflow.common.exception.ResourceNotFoundException;
import com.careflow.common.response.PagedResponse;
import com.careflow.patient.dto.CreatePatientRequest;
import com.careflow.patient.dto.PatientResponse;
import com.careflow.patient.dto.UpdatePatientRequest;
import com.careflow.patient.entity.Patient;
import com.careflow.patient.repository.PatientRepository;
import com.careflow.user.entity.User;
import com.careflow.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.util.UUID;

@Service
public class PatientService {

    private final PatientRepository patientRepository;
    private final UserRepository userRepository;

    public PatientService(PatientRepository patientRepository, UserRepository userRepository) {
        this.patientRepository = patientRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public PatientResponse createPatient(CreatePatientRequest request) {
        User user = null;
        if (request.userId() != null) {
            user = userRepository.findById(request.userId())
                    .orElseThrow(() -> new ResourceNotFoundException("User", "id", request.userId()));
            if (patientRepository.findByUserId(request.userId()).isPresent()) {
                throw new BusinessValidationException("A patient profile already exists for this user account");
            }
        }

        String reference = generatePatientReference();
        Patient patient = new Patient(
                user,
                reference,
                request.dateOfBirth(),
                request.contactNumber(),
                request.address()
        );

        Patient saved = patientRepository.save(patient);
        return PatientResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public PatientResponse getPatientById(Long id, Long currentUserId, boolean isStaff) {
        Patient patient = patientRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", "id", id));

        // Patients can only view their own profile; staff can view any
        if (!isStaff && (patient.getUser() == null || !patient.getUser().getId().equals(currentUserId))) {
            throw new AccessDeniedException("You do not have permission to view this patient profile");
        }

        return PatientResponse.fromEntity(patient);
    }

    @Transactional(readOnly = true)
    public PatientResponse getPatientByUserId(Long userId) {
        Patient patient = patientRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient profile not found for user id: " + userId));
        return PatientResponse.fromEntity(patient);
    }

    @Transactional
    public PatientResponse updatePatient(Long id, UpdatePatientRequest request, Long currentUserId, boolean isStaff) {
        Patient patient = patientRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", "id", id));

        if (!isStaff && (patient.getUser() == null || !patient.getUser().getId().equals(currentUserId))) {
            throw new AccessDeniedException("You do not have permission to update this patient profile");
        }

        patient.setDateOfBirth(request.dateOfBirth());
        patient.setContactNumber(request.contactNumber());
        patient.setAddress(request.address());

        Patient updated = patientRepository.save(patient);
        return PatientResponse.fromEntity(updated);
    }

    @Transactional(readOnly = true)
    public PagedResponse<PatientResponse> getAllPatients(Pageable pageable) {
        Page<PatientResponse> page = patientRepository.findAll(pageable)
                .map(PatientResponse::fromEntity);
        return PagedResponse.from(page);
    }

    public Patient getPatientEntity(Long id) {
        return patientRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", "id", id));
    }

    private String generatePatientReference() {
        int currentYear = Year.now().getValue();
        String shortUuid = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return String.format("PAT-%d-%s", currentYear, shortUuid);
    }
}
