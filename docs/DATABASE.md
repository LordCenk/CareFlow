# CareFlow — Database Design & Schema Documentation

The CareFlow data layer uses a normalized relational schema managed with versioned **Flyway** migrations. It supports both **MySQL 8.4 LTS** in production and containerized environments, and **In-Memory H2** in MySQL compatibility mode for rapid unit and integration testing.

---

## 1. Entity-Relationship (ER) Diagram

```mermaid
erDiagram
    USERS ||--o| PATIENTS : "links to"
    USERS ||--o| DOCTORS : "authenticates"
    DEPARTMENTS ||--o{ DOCTORS : "employs"
    DOCTORS ||--o{ DOCTOR_AVAILABILITY : "configures"
    DOCTORS ||--o{ DOCTOR_LEAVE : "requests"
    DOCTORS ||--o{ APPOINTMENTS : "conducts"
    PATIENTS ||--o{ APPOINTMENTS : "schedules"
    APPOINTMENTS ||--o| QUEUE_ENTRIES : "progresses to"
    APPOINTMENTS ||--o| INVOICES : "billed by"
    INVOICES ||--o{ PAYMENT_RECORDS : "settled with"
    DOCTORS ||--o{ DOCTOR_DAILY_QUEUE_SEQUENCE : "tracks daily queue"

    USERS {
        bigint id PK
        varchar name
        varchar email UK
        varchar password_hash
        varchar role
        boolean enabled
        timestamp created_at
        timestamp updated_at
    }

    DEPARTMENTS {
        bigint id PK
        varchar name UK
        varchar description
        boolean active
        timestamp created_at
        timestamp updated_at
    }

    DOCTORS {
        bigint id PK
        bigint user_id FK, UK
        varchar doctor_reference UK
        bigint department_id FK
        varchar specialization
        decimal consultation_fee
        boolean active
        timestamp created_at
        timestamp updated_at
    }

    PATIENTS {
        bigint id PK
        bigint user_id FK, UK
        varchar patient_reference UK
        date date_of_birth
        varchar contact_number
        varchar address
        timestamp created_at
        timestamp updated_at
    }

    DOCTOR_AVAILABILITY {
        bigint id PK
        bigint doctor_id FK
        varchar day_of_week
        time start_time
        time end_time
        int slot_duration_minutes
        date effective_from
        date effective_until
        boolean active
        timestamp created_at
    }

    DOCTOR_LEAVE {
        bigint id PK
        bigint doctor_id FK
        timestamp start_date_time
        timestamp end_date_time
        varchar reason
        varchar status
        timestamp created_at
    }

    APPOINTMENTS {
        bigint id PK
        varchar appointment_reference UK
        bigint patient_id FK
        bigint doctor_id FK
        timestamp start_date_time
        timestamp end_date_time
        varchar status
        varchar reason_for_visit
        varchar cancellation_reason
        varchar created_by
        timestamp created_at
        timestamp updated_at
    }

    QUEUE_ENTRIES {
        bigint id PK
        bigint appointment_id FK, UK
        bigint doctor_id FK
        date queue_date
        int queue_number
        varchar status
        timestamp checked_in_at
        timestamp called_at
        timestamp completed_at
    }

    DOCTOR_DAILY_QUEUE_SEQUENCE {
        bigint doctor_id PK, FK
        date queue_date PK
        int last_queue_number
    }

    INVOICES {
        bigint id PK
        varchar invoice_reference UK
        bigint appointment_id FK, UK
        bigint patient_id FK
        decimal amount
        varchar currency
        varchar status
        timestamp issued_at
        timestamp paid_at
        timestamp updated_at
    }

    PAYMENT_RECORDS {
        bigint id PK
        bigint invoice_id FK
        varchar payment_reference UK
        decimal amount
        varchar status
        varchar method
        timestamp created_at
        timestamp updated_at
    }

    NOTIFICATIONS {
        bigint id PK
        varchar recipient_email
        varchar recipient_name
        varchar notification_type
        varchar subject
        text message
        varchar status
        bigint appointment_id
        timestamp scheduled_for
        timestamp sent_at
        timestamp created_at
    }

    AUDIT_LOGS {
        bigint id PK
        varchar entity_name
        bigint entity_id
        varchar action
        varchar performed_by
        timestamp timestamp
        text details
    }
```

---

## 2. Table Specifications & Indexes

### 2.1 `users`
- **Primary Key:** `id` (BIGINT AUTO_INCREMENT)
- **Unique Constraints:** `email` (VARCHAR 150)
- **Indexes:**
  - `idx_users_email` ON `users(email)`: Rapid authentication lookup.
  - `idx_users_role` ON `users(role)`: Filtering users by authority.

### 2.2 `departments`
- **Primary Key:** `id` (BIGINT AUTO_INCREMENT)
- **Unique Constraints:** `name` (VARCHAR 100)

### 2.3 `doctors`
- **Primary Key:** `id` (BIGINT AUTO_INCREMENT)
- **Foreign Keys:**
  - `user_id` REFERENCES `users(id)` ON DELETE CASCADE
  - `department_id` REFERENCES `departments(id)`
- **Unique Constraints:** `user_id`, `doctor_reference`
- **Indexes:**
  - `idx_doctors_department` ON `doctors(department_id)`
  - `idx_doctors_specialization` ON `doctors(specialization)`

### 2.4 `patients`
- **Primary Key:** `id` (BIGINT AUTO_INCREMENT)
- **Foreign Keys:**
  - `user_id` REFERENCES `users(id)` ON DELETE SET NULL (allows walk-in patient records without login accounts)
- **Unique Constraints:** `patient_reference`

### 2.5 `doctor_availability`
- **Primary Key:** `id` (BIGINT AUTO_INCREMENT)
- **Foreign Keys:** `doctor_id` REFERENCES `doctors(id)` ON DELETE CASCADE
- **Indexes:**
  - `idx_doc_avail_doctor` ON `doctor_availability(doctor_id)`
  - `idx_doc_avail_day` ON `doctor_availability(day_of_week)`

### 2.6 `doctor_leave`
- **Primary Key:** `id` (BIGINT AUTO_INCREMENT)
- **Foreign Keys:** `doctor_id` REFERENCES `doctors(id)` ON DELETE CASCADE
- **Indexes:**
  - `idx_doc_leave_interval` ON `doctor_leave(doctor_id, start_date_time, end_date_time)`: Optimizes overlap detection queries.

### 2.7 `appointments`
- **Primary Key:** `id` (BIGINT AUTO_INCREMENT)
- **Unique Constraints:** `appointment_reference`
- **Foreign Keys:**
  - `patient_id` REFERENCES `patients(id)`
  - `doctor_id` REFERENCES `doctors(id)`
- **Indexes:**
  - `idx_appt_doc_dates` ON `appointments(doctor_id, start_date_time, end_date_time, status)`: Hotspot index for conflict evaluation.
  - `idx_appt_patient` ON `appointments(patient_id, status)`: Rapid patient history lookup and patient double-booking check.
  - `idx_appt_status` ON `appointments(status)`: Status transition and reminder scanning.

### 2.8 `queue_entries`
- **Primary Key:** `id` (BIGINT AUTO_INCREMENT)
- **Unique Constraints:**
  - `appointment_id`: One queue entry per appointment.
  - `uk_queue_doctor_date_num` ON `(doctor_id, queue_date, queue_number)`: Strict uniqueness of queue positions per doctor per day.
- **Foreign Keys:**
  - `appointment_id` REFERENCES `appointments(id)` ON DELETE CASCADE
  - `doctor_id` REFERENCES `doctors(id)`

### 2.9 `doctor_daily_queue_sequence`
- **Primary Key:** `(doctor_id, queue_date)`
- **Foreign Keys:** `doctor_id` REFERENCES `doctors(id)` ON DELETE CASCADE
- **Purpose:** Used with pessimistic write locking to atomically increment and allocate sequential queue numbers without table scans or collision errors.

### 2.10 `invoices` & `payment_records`
- **Primary Key:** `id` (BIGINT AUTO_INCREMENT)
- **Unique Constraints:**
  - `invoice_reference` on `invoices`
  - `appointment_id` on `invoices`: Guarantees exactly one invoice per appointment.
  - `payment_reference` on `payment_records`
- **Foreign Keys:**
  - `appointment_id` REFERENCES `appointments(id)`
  - `patient_id` REFERENCES `patients(id)`
  - `invoice_id` REFERENCES `invoices(id)`

---

## 3. Flyway Migration Versioning

CareFlow uses Flyway migrations stored in `src/main/resources/db/migration`:

1. `V1__create_users_and_departments.sql`: Users table with BCrypt hash storage, roles, and hospital departments.
2. `V2__create_patients_and_doctors.sql`: Clinical doctor profiles, consultation pricing, and patient demographics.
3. `V3__create_doctor_availability_and_leave.sql`: Weekly recurring availability schedules and doctor leave intervals.
4. `V4__create_appointments_and_queue.sql`: Clinical appointments and outpatient queue table with composite uniqueness constraint.
5. `V5__create_billing_and_notifications.sql`: Consultation invoices, simulated payment logs, and notification dispatch records.
6. `V6__create_audit_and_queue_counter.sql`: Comprehensive audit logging and atomic daily queue sequence counter.
7. `V7__seed_synthetic_data.sql`: Seed data for development and testing: 7 accounts (admin, receptionist, 3 doctors, 2 patients), 5 departments, 3 doctor recurring schedules.
