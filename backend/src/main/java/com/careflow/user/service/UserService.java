package com.careflow.user.service;

import com.careflow.common.exception.DuplicateResourceException;
import com.careflow.common.exception.ResourceNotFoundException;
import com.careflow.doctor.entity.Doctor;
import com.careflow.doctor.repository.DoctorRepository;
import com.careflow.patient.entity.Patient;
import com.careflow.patient.repository.PatientRepository;
import com.careflow.security.CustomUserPrincipal;
import com.careflow.security.JwtTokenProvider;
import com.careflow.user.dto.AuthResponse;
import com.careflow.user.dto.LoginRequest;
import com.careflow.user.dto.RegisterRequest;
import com.careflow.user.dto.UserProfileResponse;
import com.careflow.user.entity.User;
import com.careflow.user.entity.UserRole;
import com.careflow.user.repository.UserRepository;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final AuthenticationManager authenticationManager;

    public UserService(
            UserRepository userRepository,
            PatientRepository patientRepository,
            DoctorRepository doctorRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider tokenProvider,
            AuthenticationManager authenticationManager
    ) {
        this.userRepository = userRepository;
        this.patientRepository = patientRepository;
        this.doctorRepository = doctorRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.authenticationManager = authenticationManager;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException("An account with email " + request.email() + " already exists");
        }

        UserRole role = request.role() != null ? request.role() : UserRole.PATIENT;

        User user = new User(
                request.name(),
                request.email(),
                passwordEncoder.encode(request.password()),
                role
        );
        User savedUser = userRepository.save(user);

        Long patientId = null;
        if (role == UserRole.PATIENT) {
            String contactNumber = request.contactNumber() != null ? request.contactNumber() : "N/A";
            String reference = String.format("PAT-%d-%s", Year.now().getValue(), UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            Patient patient = new Patient(savedUser, reference, null, contactNumber, request.address());
            Patient savedPatient = patientRepository.save(patient);
            patientId = savedPatient.getId();
        }

        String token = tokenProvider.generateToken(savedUser);

        return new AuthResponse(
                token,
                "Bearer",
                savedUser.getId(),
                savedUser.getName(),
                savedUser.getEmail(),
                savedUser.getRole(),
                patientId,
                null,
                tokenProvider.getExpirationMs()
        );
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + request.email()));

        Long patientId = patientRepository.findByUserId(user.getId()).map(Patient::getId).orElse(null);
        Long doctorId = doctorRepository.findByUserId(user.getId()).map(Doctor::getId).orElse(null);

        String token = tokenProvider.generateToken(user);

        return new AuthResponse(
                token,
                "Bearer",
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                patientId,
                doctorId,
                tokenProvider.getExpirationMs()
        );
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getCurrentUser(CustomUserPrincipal principal) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", principal.getId()));

        Long patientId = patientRepository.findByUserId(user.getId()).map(Patient::getId).orElse(null);
        Long doctorId = doctorRepository.findByUserId(user.getId()).map(Doctor::getId).orElse(null);

        return new UserProfileResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                user.isEnabled(),
                patientId,
                doctorId,
                user.getCreatedAt()
        );
    }
}
