# CareFlow — Technical Interview Guide & Architecture Deep-Dive

This guide is designed for explaining CareFlow with confidence during senior Java backend engineering, system design, and software architect interviews.

---

## 1. Why Was This Technology Stack Chosen?

| Technology | Selection Rationale | Interview Talking Point |
|---|---|---|
| **Java 21 LTS** | Modern LTS release offering Native Records, Virtual Threads compatibility, Pattern Matching, and enhanced switch expressions. | *"We leveraged Java 21 immutable records for all DTOs and API contracts. This guarantees thread-safe, immutable data carriers without Lombok bloat or boilerplate getters."* |
| **Spring Boot 3.3.4** | Industry-standard enterprise framework with first-class Jakarta EE 10 support and streamlined configuration. | *"We chose Spring Boot 3.3 for its production-grade dependency management, transaction orchestration, and integration with Spring Data JPA 3."* |
| **Spring Data JPA & Hibernate 6.5** | Declarative repository abstraction, native query projection, and fine-grained pessimistic locking support. | *"Hibernate 6 provides semantic query parsing and native pessimistic lock translation (`SELECT ... FOR UPDATE`), which is core to our double-booking prevention."* |
| **Flyway Database Migrations** | Deterministic, reproducible, code-versioned database evolution. | *"Flyway ensures database schemas are treated as first-class code artifacts. Every migration from V1 to V7 is idempotent, traceable, and version-controlled across environments."* |
| **MySQL 8.4 LTS** | Battle-tested relational database with ACID guarantees, row-level locking, and transactional indexes. | *"MySQL 8.4 was chosen for its mature InnoDB storage engine, which provides ACID transactional safety and row-level locking needed for our concurrency engine."* |
| **Spring Security & JJWT 0.12** | Stateless authentication using HMAC-SHA256 signed bearer tokens. | *"We implemented stateless security with custom OncePerRequestFilter and method-level `@PreAuthorize` annotations, eliminating session affinity requirements."* |
| **Testcontainers** | Real database testing inside ephemeral Docker containers. | *"Rather than relying exclusively on in-memory mocks, Testcontainers enables running concurrency tests against an actual MySQL instance to verify true database locking semantics."* |

---

## 2. Core Classes & Their Architecture Responsibilities

### 2.1 `AppointmentService.java`
- **Responsibility:** The core scheduling coordinator.
- **Key Methods:**
  - `bookAppointment`: Validates timing rules, locks the doctor and patient with `PESSIMISTIC_WRITE`, checks interval overlaps, creates `BOOKED` record, and triggers confirmation alerts.
  - `rescheduleAppointment`: Atomically shifts appointment boundaries under pessimistic lock while ensuring the original slot is released and the new slot has no conflict.
  - `cancelAppointment`: Enforces role-based cancellation deadlines (e.g., patient must cancel >= 2h prior; staff can override), transitions status to `CANCELLED`, releases the slot, and updates outpatient queue state.
  - `updateStatus`: Enforces strict state transitions (`BOOKED` -> `CHECKED_IN` -> `IN_CONSULTATION` -> `COMPLETED`).

### 2.2 `DoctorScheduleService.java`
- **Responsibility:** Time-slot calculation and availability engine.
- **Algorithm:**
  - Evaluates doctor's recurring weekly working hours (`DoctorAvailability`).
  - Iterates in discrete intervals of `slotDurationMinutes`.
  - Dynamically filters out slots overlapping with approved doctor leaves or existing non-cancelled appointments using the interval formula: `existing.start < slot.end AND existing.end > slot.start`.

### 2.3 `QueueService.java`
- **Responsibility:** Outpatient clinic queue orchestrator.
- **Concurrency Mechanism:**
  - Uses `doctorRepository.findByIdWithLock(doctorId)` to serialize check-in execution.
  - Uses `DoctorDailyQueueSequence` to allocate sequential, gapless queue numbers (1, 2, 3...) per doctor per day.
  - Guarantees zero duplicate queue numbers and zero primary key violations under concurrent check-ins.

### 2.4 `BillingService.java`
- **Responsibility:** Server-side financial billing and simulated settlement.
- **Security Rule:** Never trusts client-supplied amounts. The consultation fee is strictly derived from `appointment.getDoctor().getConsultationFee()`.

---

## 3. Transaction Boundaries & Isolation Levels

### Q: Why do you specify `@Transactional(isolation = Isolation.READ_COMMITTED)`?
**Answer:**
Under default MySQL `REPEATABLE_READ`, a transaction creates a consistent snapshot on its first read. If a concurrent transaction commits a new row (phantom read), a subsequent non-locking `SELECT` within the original transaction will not see that row! 
By setting `READ_COMMITTED` combined with `SELECT ... FOR UPDATE` (pessimistic write locking), the lock waits on the row and immediately reads the latest committed state, guaranteeing that slot checks always evaluate real-time ground truth.

### Q: How do Spring transactions work under the hood?
**Answer:**
Spring creates a dynamic CGLIB or JDK dynamic proxy around `@Transactional` classes. When a method is invoked:
1. `TransactionInterceptor` intercepts the call.
2. It looks up `PlatformTransactionManager` (specifically `JpaTransactionManager`).
3. It opens an entity manager/database connection, disables auto-commit (`connection.setAutoCommit(false)`), and binds it to `TransactionSynchronizationManager` on the current thread (`ThreadLocal`).
4. If the method completes normally, it commits the connection. If an unchecked exception (`RuntimeException` or `Error`) is thrown, it executes `connection.rollback()`.

---

## 4. Concurrency Control: The Deep-Dive

### Q: Why doesn't a MySQL UNIQUE constraint prevent overlapping appointments?
**Answer:**
A MySQL unique constraint works on discrete tuples, such as `(doctor_id, start_time)`. If Patient A books `09:00 - 10:00` and Patient B books `09:30 - 10:30`, the start times are distinct (`09:00 != 09:30`). A unique constraint passes both, yet they overlap from `09:30 to 10:00`. PostgreSQL offers exclusion constraints (`EXCLUDE USING gist`), but MySQL does not support this.

### Q: How does CareFlow prevent this race condition?
**Answer:**
We use **Pessimistic Write Locking** on the `Doctor` table row:
```java
Doctor doctor = doctorRepository.findByIdWithLock(request.doctorId());
```
In MySQL InnoDB, this translates to:
```sql
SELECT * FROM doctors WHERE id = ? FOR UPDATE;
```
Because the `Doctor` record already exists, InnoDB places an exclusive row lock on this record. Any other transaction attempting to book a slot for the same doctor executes the same query and is blocked by the database engine until our transaction finishes. 

While holding this lock, we query:
```sql
SELECT a FROM Appointment a 
WHERE a.doctor.id = :doctorId 
  AND a.status IN ('BOOKED', 'CHECKED_IN', 'IN_CONSULTATION')
  AND a.startDateTime < :end 
  AND a.endDateTime > :start;
```
If any row returns, we throw `AppointmentConflictException` (HTTP 409). Once our transaction inserts and commits, the blocked request unblocks, re-evaluates the query, sees our newly committed appointment, and is safely rejected.

---

## 5. 15 Frequently Asked Java Backend Interview Questions & Answers

### 1. What happens if a patient cancels an appointment while a receptionist is checking them in?
> **Answer:** Both operations are transactional. If check-in commits first, status becomes `CHECKED_IN`; then cancellation sets status to `CANCELLED` and marks the queue entry as `CANCELLED`. If cancellation commits first, status becomes `CANCELLED`; when check-in attempts to process, `QueueService.checkIn` checks `if (appointment.getStatus() != AppointmentStatus.BOOKED)` and throws an `InvalidStateTransitionException`, preventing an invalid check-in.

### 2. Why use Java 21 Records instead of Lombok `@Data`?
> **Answer:** Records provide compiler-enforced immutability, built-in value-based equality (`equals`/`hashCode`), concise syntax, and native deconstruction in pattern matching without relying on annotation processing bytecode manipulation.

### 3. How do you prevent N+1 query problems in JPA?
> **Answer:** By using JPQL `JOIN FETCH` queries in repositories for eager fetching of parent relationships (e.g. `SELECT d FROM Doctor d JOIN FETCH d.department JOIN FETCH d.user WHERE d.id = :id`), avoiding lazy-proxy query storms.

### 4. Why is `open-in-view: false` configured?
> **Answer:** `OpenSessionInView` keeps the database connection and Hibernate session open during the entire view/controller rendering phase. Disabling it (`open-in-view: false`) prevents connection pool exhaustion and forces developers to fetch needed data inside explicit service transaction boundaries.

### 5. Why is BigDecimal used for money instead of double or float?
> **Answer:** `float` and `double` use IEEE 754 floating-point binary representation, which cannot accurately represent fractional decimal numbers like 0.1 or 0.05, leading to rounding discrepancies. `BigDecimal` provides arbitrary-precision signed decimal arithmetic.

### 6. How is queue numbering made atomic without gaps?
> **Answer:** We created a `doctor_daily_queue_sequence` table keyed by `(doctor_id, queue_date)`. During check-in, the transaction locks the doctor row, reads the sequence counter, increments it by 1, and saves it in the same transaction as the `QueueEntry`.

### 7. What is the difference between `PESSIMISTIC_READ` and `PESSIMISTIC_WRITE`?
> **Answer:** `PESSIMISTIC_READ` issues a shared lock (`LOCK IN SHARE MODE`), allowing other transactions to read but not update. `PESSIMISTIC_WRITE` issues an exclusive lock (`FOR UPDATE`), blocking both other writers and readers requesting locks.

### 8. How does JWT authentication stay stateless?
> **Answer:** The JWT payload contains all necessary claims (`userId`, `role`, `email`). The server cryptographically signs the token with a HMAC-SHA256 secret. When a request arrives, `JwtAuthenticationFilter` validates the signature and expiration mathematically without querying a server-side session store.

### 9. Why is `REQUIRES_NEW` used for audit logging?
> **Answer:** `@Transactional(propagation = Propagation.REQUIRES_NEW)` suspends any active transaction and starts an independent physical database transaction. If the primary business transaction rolls back (e.g. due to validation failure), the audit log is still committed.

### 10. How does CareFlow prevent SQL injection?
> **Answer:** Spring Data JPA and Hibernate use parameterized JDBC PreparedStatement queries (`?` or named parameters `:id`). User input is treated strictly as literal data rather than executable SQL syntax.

### 11. What is the difference between `@Mock` and `@MockBean`?
> **Answer:** `@Mock` is provided by Mockito for plain unit tests without starting the Spring context. `@MockBean` is a Spring Boot Test annotation that registers a mock in the Spring `ApplicationContext`, replacing any existing bean of the same type.

### 12. How does the system handle timezones?
> **Answer:** We configure MySQL with `serverTimezone=UTC`. Instant timestamps (`Instant.now()`) are stored as UTC in the database, ensuring consistency regardless of server geographical location.

### 13. What is the purpose of Flyway baseline-on-migrate?
> **Answer:** `baseline-on-migrate: true` allows Flyway to initialize a schema history table on an existing non-empty database without failing, establishing a baseline baseline version before applying incremental scripts.

### 14. What are the limitations of the doctor-level locking strategy?
> **Answer:** It serializes bookings per doctor. If a popular specialist receives 500 simultaneous booking requests across different days of the year, all 500 requests must queue on that doctor's row lock. 

### 15. How would you scale this system to 100,000 requests per second?
> **Answer:**
> 1. **Read Scaling:** Deploy read-replicas for `GET /api/v1/appointments/available-slots`, with Redis caching of daily schedules invalidated when leaves or bookings occur.
> 2. **Finer-Grained Locking:** Materialize time slots into a `doctor_slots` table with unique constraint `(doctor_id, slot_date_time)` or use Redis distributed locking (`Redlock`) with key `lock:doctor:{id}:slot:{timestamp}`.
> 3. **Asynchronous Events:** Publish `AppointmentBookedEvent` to Kafka; asynchronous consumers handle email reminders, SMS notifications, and billing generation.
