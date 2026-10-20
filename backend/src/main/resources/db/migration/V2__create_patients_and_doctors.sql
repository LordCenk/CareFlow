CREATE TABLE patients (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT UNIQUE,
    patient_reference VARCHAR(40) NOT NULL UNIQUE,
    date_of_birth DATE,
    contact_number VARCHAR(30) NOT NULL,
    address VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_patients_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
);

CREATE INDEX idx_patients_user_id ON patients(user_id);
CREATE INDEX idx_patients_ref ON patients(patient_reference);

CREATE TABLE doctors (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    doctor_reference VARCHAR(40) NOT NULL UNIQUE,
    department_id BIGINT NOT NULL,
    specialization VARCHAR(100) NOT NULL,
    consultation_fee DECIMAL(10, 2) NOT NULL DEFAULT 50.00,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_doctors_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_doctors_department FOREIGN KEY (department_id) REFERENCES departments(id)
);

CREATE INDEX idx_doctors_department ON doctors(department_id);
CREATE INDEX idx_doctors_specialization ON doctors(specialization);
