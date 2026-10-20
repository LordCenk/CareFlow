CREATE TABLE invoices (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    invoice_reference VARCHAR(40) NOT NULL UNIQUE,
    appointment_id BIGINT NOT NULL UNIQUE,
    patient_id BIGINT NOT NULL,
    amount DECIMAL(10, 2) NOT NULL,
    currency VARCHAR(10) NOT NULL DEFAULT 'USD',
    status VARCHAR(20) NOT NULL DEFAULT 'ISSUED',
    issued_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    paid_at TIMESTAMP NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_invoices_appt FOREIGN KEY (appointment_id) REFERENCES appointments(id),
    CONSTRAINT fk_invoices_patient FOREIGN KEY (patient_id) REFERENCES patients(id)
);

CREATE INDEX idx_invoice_patient ON invoices(patient_id);
CREATE INDEX idx_invoice_status ON invoices(status);

CREATE TABLE payment_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    invoice_id BIGINT NOT NULL,
    payment_reference VARCHAR(40) NOT NULL UNIQUE,
    amount DECIMAL(10, 2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'SUCCESS',
    method VARCHAR(30) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_payments_invoice FOREIGN KEY (invoice_id) REFERENCES invoices(id)
);

CREATE INDEX idx_payment_invoice ON payment_records(invoice_id);

CREATE TABLE notifications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    recipient_email VARCHAR(150) NOT NULL,
    recipient_name VARCHAR(120),
    notification_type VARCHAR(50) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    appointment_id BIGINT,
    scheduled_for TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_notif_status_sched ON notifications(status, scheduled_for);
