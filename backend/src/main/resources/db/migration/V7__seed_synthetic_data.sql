-- Seed Users (password for all seeded accounts is 'password123')
-- Hash: $2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG
INSERT INTO users (id, name, email, password_hash, role, enabled, created_at, updated_at) VALUES
(1, 'System Administrator', 'admin@careflow.com', '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', 'ADMIN', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(2, 'Sarah Jenkins', 'receptionist@careflow.com', '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', 'RECEPTIONIST', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(3, 'Dr. John Smith', 'dr.smith@careflow.com', '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', 'DOCTOR', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(4, 'Dr. Sarah Jones', 'dr.jones@careflow.com', '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', 'DOCTOR', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(5, 'Dr. Rajesh Patel', 'dr.patel@careflow.com', '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', 'DOCTOR', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(6, 'Alice Walker', 'patient.alice@careflow.com', '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', 'PATIENT', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(7, 'Bob Davis', 'patient.bob@careflow.com', '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', 'PATIENT', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Seed Departments
INSERT INTO departments (id, name, description, active, created_at, updated_at) VALUES
(1, 'Cardiology', 'Specialized cardiovascular diagnostics, heart health, and monitoring.', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(2, 'Dermatology', 'Comprehensive skin, scalp, and dermatological clinical care.', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(3, 'General Medicine', 'Primary healthcare, outpatient diagnostics, and wellness assessments.', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(4, 'Orthopedics', 'Musculoskeletal system, joint health, and fracture treatments.', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(5, 'Pediatrics', 'Comprehensive healthcare and developmental wellness for children.', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Seed Doctors
INSERT INTO doctors (id, user_id, doctor_reference, department_id, specialization, consultation_fee, active, created_at, updated_at) VALUES
(1, 3, 'DOC-2026-0001', 1, 'Cardiologist', 75.00, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(2, 4, 'DOC-2026-0002', 2, 'Dermatologist', 60.00, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(3, 5, 'DOC-2026-0003', 3, 'Internal Medicine Specialist', 50.00, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Seed Patients
INSERT INTO patients (id, user_id, patient_reference, date_of_birth, contact_number, address, created_at, updated_at) VALUES
(1, 6, 'PAT-2026-0001', '1990-05-15', '+1-555-0101', '123 Maple Street, Metro City', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(2, 7, 'PAT-2026-0002', '1985-11-20', '+1-555-0102', '456 Oak Avenue, Metro City', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Seed Doctor Availability (Recurring schedules)
-- Dr. Smith: Monday - Friday, 09:00 - 13:00, 30 min slots
INSERT INTO doctor_availability (doctor_id, day_of_week, start_time, end_time, slot_duration_minutes, active) VALUES
(1, 'MONDAY', '09:00:00', '13:00:00', 30, TRUE),
(1, 'TUESDAY', '09:00:00', '13:00:00', 30, TRUE),
(1, 'WEDNESDAY', '09:00:00', '13:00:00', 30, TRUE),
(1, 'THURSDAY', '09:00:00', '13:00:00', 30, TRUE),
(1, 'FRIDAY', '09:00:00', '13:00:00', 30, TRUE);

-- Dr. Jones: Monday - Friday, 10:00 - 15:00, 30 min slots
INSERT INTO doctor_availability (doctor_id, day_of_week, start_time, end_time, slot_duration_minutes, active) VALUES
(2, 'MONDAY', '10:00:00', '15:00:00', 30, TRUE),
(2, 'TUESDAY', '10:00:00', '15:00:00', 30, TRUE),
(2, 'WEDNESDAY', '10:00:00', '15:00:00', 30, TRUE),
(2, 'THURSDAY', '10:00:00', '15:00:00', 30, TRUE),
(2, 'FRIDAY', '10:00:00', '15:00:00', 30, TRUE);

-- Dr. Patel: Monday - Saturday, 08:30 - 14:30, 30 min slots
INSERT INTO doctor_availability (doctor_id, day_of_week, start_time, end_time, slot_duration_minutes, active) VALUES
(3, 'MONDAY', '08:30:00', '14:30:00', 30, TRUE),
(3, 'TUESDAY', '08:30:00', '14:30:00', 30, TRUE),
(3, 'WEDNESDAY', '08:30:00', '14:30:00', 30, TRUE),
(3, 'THURSDAY', '08:30:00', '14:30:00', 30, TRUE),
(3, 'FRIDAY', '08:30:00', '14:30:00', 30, TRUE),
(3, 'SATURDAY', '08:30:00', '14:30:00', 30, TRUE);
