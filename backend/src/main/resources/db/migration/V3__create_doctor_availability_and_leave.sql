CREATE TABLE doctor_availability (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    doctor_id BIGINT NOT NULL,
    day_of_week VARCHAR(15) NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    slot_duration_minutes INT NOT NULL DEFAULT 30,
    effective_from DATE,
    effective_until DATE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_doc_avail_doctor FOREIGN KEY (doctor_id) REFERENCES doctors(id) ON DELETE CASCADE
);

CREATE INDEX idx_doc_avail_doctor ON doctor_availability(doctor_id);
CREATE INDEX idx_doc_avail_day ON doctor_availability(day_of_week);

CREATE TABLE doctor_leave (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    doctor_id BIGINT NOT NULL,
    start_date_time TIMESTAMP NOT NULL,
    end_date_time TIMESTAMP NOT NULL,
    reason VARCHAR(255),
    status VARCHAR(20) NOT NULL DEFAULT 'APPROVED',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_doc_leave_doctor FOREIGN KEY (doctor_id) REFERENCES doctors(id) ON DELETE CASCADE
);

CREATE INDEX idx_doc_leave_interval ON doctor_leave(doctor_id, start_date_time, end_date_time);
