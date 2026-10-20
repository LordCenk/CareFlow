CREATE TABLE appointments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    appointment_reference VARCHAR(40) NOT NULL UNIQUE,
    patient_id BIGINT NOT NULL,
    doctor_id BIGINT NOT NULL,
    start_date_time TIMESTAMP NOT NULL,
    end_date_time TIMESTAMP NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'BOOKED',
    reason_for_visit VARCHAR(500),
    cancellation_reason VARCHAR(500),
    created_by VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_appts_patient FOREIGN KEY (patient_id) REFERENCES patients(id),
    CONSTRAINT fk_appts_doctor FOREIGN KEY (doctor_id) REFERENCES doctors(id)
);

CREATE INDEX idx_appt_doc_dates ON appointments(doctor_id, start_date_time, end_date_time, status);
CREATE INDEX idx_appt_patient ON appointments(patient_id, status);
CREATE INDEX idx_appt_status ON appointments(status);

CREATE TABLE queue_entries (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    appointment_id BIGINT NOT NULL UNIQUE,
    doctor_id BIGINT NOT NULL,
    queue_date DATE NOT NULL,
    queue_number INT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'WAITING',
    checked_in_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    called_at TIMESTAMP NULL,
    completed_at TIMESTAMP NULL,
    CONSTRAINT fk_queue_appt FOREIGN KEY (appointment_id) REFERENCES appointments(id) ON DELETE CASCADE,
    CONSTRAINT fk_queue_doctor FOREIGN KEY (doctor_id) REFERENCES doctors(id),
    CONSTRAINT uk_queue_doctor_date_num UNIQUE (doctor_id, queue_date, queue_number)
);

CREATE INDEX idx_queue_doctor_date ON queue_entries(doctor_id, queue_date, status);
