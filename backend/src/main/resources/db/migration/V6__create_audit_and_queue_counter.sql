CREATE TABLE audit_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    entity_name VARCHAR(50) NOT NULL,
    entity_id BIGINT,
    action VARCHAR(50) NOT NULL,
    performed_by VARCHAR(150) NOT NULL,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    details TEXT
);

CREATE INDEX idx_audit_entity ON audit_logs(entity_name, entity_id);
CREATE INDEX idx_audit_timestamp ON audit_logs(timestamp);

-- Sequence counter table for atomic queue number allocation per doctor and date
CREATE TABLE doctor_daily_queue_sequence (
    doctor_id BIGINT NOT NULL,
    queue_date DATE NOT NULL,
    last_queue_number INT NOT NULL DEFAULT 0,
    PRIMARY KEY (doctor_id, queue_date),
    CONSTRAINT fk_queue_seq_doctor FOREIGN KEY (doctor_id) REFERENCES doctors(id) ON DELETE CASCADE
);
