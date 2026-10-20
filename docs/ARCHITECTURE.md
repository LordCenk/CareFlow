# CareFlow — System Architecture & Design Documentation

CareFlow is designed as a **Modular Monolith** backend application built with **Java 21 LTS** and **Spring Boot 3.3.4**. It manages core hospital workflows: patient registration, doctor schedules, real-time appointment booking, concurrent slot protection, outpatient queue progression, and billing.

---

## 1. High-Level Architectural Diagram

```mermaid
flowchart TD
    Client["Clients (Web / Mobile / Receptionist UI / Swagger UI)"] -->|HTTPS / REST / JWT Bearer| Gateway["Spring Security Filter Chain"]
    Gateway --> JwtFilter["JwtAuthenticationFilter"]
    JwtFilter --> Controllers["REST Controllers (/api/v1/*)"]

    subgraph ApplicationLayer ["CareFlow Application Layer"]
        Controllers --> UserModule["User & Auth Module"]
        Controllers --> PatientModule["Patient Module"]
        Controllers --> DoctorModule["Doctor & Department Module"]
        Controllers --> SchedModule["Scheduling & Availability Module"]
        Controllers --> ApptModule["Appointment Module"]
        Controllers --> QueueModule["Outpatient Queue Module"]
        Controllers --> BillModule["Billing & Payments Module"]
        
        ApptModule -.->|Pessimistic Lock| SchedModule
        ApptModule -.->|Triggers Alert| NotifModule["Notification Module (Scheduled Reminders)"]
        ApptModule -.->|Audit Event| AuditModule["Audit Trail Module"]
        QueueModule -.->|Transitions Status| ApptModule
        BillModule -.->|Calculates Consultation Fee| DoctorModule
    end

    subgraph PersistenceLayer ["Persistence Layer"]
        UserModule --> JpaRepo["Spring Data JPA Repositories"]
        PatientModule --> JpaRepo
        DoctorModule --> JpaRepo
        SchedModule --> JpaRepo
        ApptModule --> JpaRepo
        QueueModule --> JpaRepo
        BillModule --> JpaRepo
        JpaRepo --> HibernateORM["Hibernate ORM 6.5"]
    end

    subgraph DataStore ["Data Store"]
        HibernateORM --> Flyway["Flyway Migration Engine (V1..V7)"]
        Flyway --> Database[("MySQL 8.4 LTS / In-Memory H2")]
    end
```

---

## 2. Package Responsibilities

The codebase follows strict package-by-feature modular monolith organization:

| Package | Responsibility |
|---|---|
| `com.careflow.common` | Shared infrastructure: generic `ApiResponse<T>`, `PagedResponse<T>`, domain exceptions, and global centralized `@RestControllerAdvice`. |
| `com.careflow.config` | Application-level beans: `OpenApiConfig` (Swagger 3), `DataInitializer` (credential synchronization). |
| `com.careflow.security` | Stateless security: `JwtTokenProvider`, `JwtAuthenticationFilter`, `CustomUserPrincipal`, `CustomUserDetailsService`, and `SecurityConfig`. |
| `com.careflow.user` | Authentication, password encryption, registration, role definitions (`ADMIN`, `DOCTOR`, `RECEPTIONIST`, `PATIENT`), and current user profile. |
| `com.careflow.patient` | Patient clinical demographic records, reference generation (`PAT-YYYY-XXXX`), and patient-level ownership access checks. |
| `com.careflow.department` | Hospital department directory, status toggling, and specialty management. |
| `com.careflow.doctor` | Doctor professional records, specialization mapping, consultation fee rates, and pessimistic lock entry points. |
| `com.careflow.scheduling` | Recurring weekly availability rules, doctor leave/vacation schedules, and real-time slot generation algorithms. |
| `com.careflow.appointment` | Core appointment engine: booking transactions, interval overlap detection, state machine, rescheduling, and cancellation policies. |
| `com.careflow.queue` | Outpatient daily queue management, atomic sequence generation (`doctor_daily_queue_sequence`), and consultation progression. |
| `com.careflow.billing` | Financial accounting: server-side consultation fee derivation, invoice generation, and simulated payment transactions. |
| `com.careflow.notification` | Communication engine: booking confirmations, cancellation alerts, rescheduling notices, and hourly background reminder tasks (`@Scheduled`). |
| `com.careflow.audit` | Compliance logging: asynchronous/isolated recording (`REQUIRES_NEW`) of sensitive operational events. |

---

## 3. Application Layers & Principles

### 3.1 DTOs and Data Immutability
- **Java 21 Records:** Request payloads (`BookAppointmentRequest`, `CreatePatientRequest`, `ProcessPaymentRequest`) and responses (`AppointmentResponse`, `InvoiceResponse`) are defined as immutable Java 21 `record`s.
- **Entity Encapsulation:** JPA entities are never returned directly from controllers. This prevents accidental lazy-loading serialization errors (`LazyInitializationException`) and avoids exposing database internals.

### 3.2 Explicit Transaction Boundaries
- All state-altering operations use explicit `@Transactional` annotations.
- Hotspot methods like `bookAppointment` and `checkIn` specify `@Transactional(isolation = Isolation.READ_COMMITTED)`.
- Audit logging uses `@Transactional(propagation = Propagation.REQUIRES_NEW)` to ensure audit logs persist even if an outer business transaction aborts.

### 3.3 Security & Role-Based Access Control (RBAC)
- All requests pass through `JwtAuthenticationFilter`, which parses HMAC-SHA256 bearer tokens.
- Method-level security is enforced via `@PreAuthorize("hasRole('ADMIN')")` or `@PreAuthorize("hasAnyRole(...)")`.
- Ownership verification ensures patients only access their own appointments, invoices, and medical data.

---

## 4. Key Workflows

### 4.1 Real-Time Appointment Booking Workflow

```mermaid
sequenceDiagram
    autonumber
    actor Patient
    participant Controller as AppointmentController
    participant Service as AppointmentService
    participant DoctorRepo as DoctorRepository
    participant ApptRepo as AppointmentRepository
    participant NotifService as NotificationService
    participant DB as MySQL Database

    Patient->>Controller: POST /api/v1/appointments {patientId, doctorId, start, end}
    Controller->>Service: bookAppointment(request)
    Note over Service: Start @Transactional
    Service->>DoctorRepo: findByIdWithLock(doctorId) [SELECT ... FOR UPDATE]
    DoctorRepo->>DB: Acquire Pessimistic Write Lock on Doctor row
    Service->>Service: Validate working hours & slot duration
    Service->>DB: Check Doctor Leaves (overlapping requested window)
    Service->>ApptRepo: findConflictingDoctorAppointments(doctorId, start, end)
    alt Overlapping Appointment Exists
        Service-->>Controller: Throw AppointmentConflictException (409 Conflict)
        Controller-->>Patient: 409 Conflict ("Slot already booked")
    else Slot is Available
        Service->>ApptRepo: save(Appointment status=BOOKED)
        ApptRepo->>DB: INSERT into appointments
        Service->>NotifService: sendBookingConfirmation(appointment)
        Service->>DB: INSERT into audit_logs
        Note over Service: Commit Transaction & Release Lock
        Service-->>Controller: Return AppointmentResponse
        Controller-->>Patient: 201 Created (Appointment Reference)
    end
```

### 4.2 Outpatient Queue Progression Workflow

```mermaid
stateDiagram-v2
    [*] --> BOOKED: Patient books appointment
    BOOKED --> CHECKED_IN: Patient arrives on appointment day (assigned atomic queue #)
    BOOKED --> CANCELLED: Patient or staff cancels appointment
    BOOKED --> NO_SHOW: Patient fails to present
    
    CHECKED_IN --> CALLED: Receptionist / Doctor calls queue number
    CALLED --> IN_CONSULTATION: Patient enters examination room
    CALLED --> NO_SHOW: Patient does not respond
    
    IN_CONSULTATION --> COMPLETED: Consultation finished
    COMPLETED --> [*]
    CANCELLED --> [*]
    NO_SHOW --> [*]
```
