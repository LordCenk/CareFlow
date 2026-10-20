# CareFlow — Appointment Concurrency & Locking Strategy

One of the most critical engineering challenges in healthcare scheduling systems is preventing **double bookings** and **overlapping appointments** when concurrent requests target the same doctor.

This document details why standard relational database constraints are insufficient, how CareFlow achieves 100% serialized slot reservation through database-backed locking, the outpatient queue allocation strategy, and the empirical test evidence verifying the implementation.

---

## 1. The Core Problem: Arbitrary Interval Overlaps in MySQL

In relational databases like PostgreSQL, exclusion constraints (`EXCLUDE USING gist (doctor_id WITH =, time_range WITH &&)`) can enforce non-overlapping time ranges at the database engine level. 

However, in **MySQL 8.4 LTS**, unique constraints can only enforce exact discrete values (e.g. `UNIQUE (doctor_id, start_time)`). A simple unique constraint fails in the following realistic scenarios:
- Request A books `09:00 - 10:00`.
- Request B books `09:30 - 10:30`.
- The start times are distinct (`09:00 != 09:30`), so a unique constraint on start time would erroneously accept both, resulting in an illegal 30-minute clinical double booking.

Furthermore, relying solely on an application-level `SELECT ... WHERE overlap` before an `INSERT` inside a standard `@Transactional` method is prone to race conditions under `READ_COMMITTED` isolation:
1. Transaction 1 executes `SELECT` -> finds no conflict.
2. Transaction 2 executes `SELECT` -> finds no conflict.
3. Transaction 1 executes `INSERT` -> succeeds and commits.
4. Transaction 2 executes `INSERT` -> succeeds and commits.
5. **Result: Double booking occurs.**

---

## 2. CareFlow Concurrency Architecture

CareFlow resolves this problem using a database-backed **Pessimistic Write Locking** strategy combined with transactional conflict queries.

### 2.1 The Doctor Row as a Concurrency Mutex

Every appointment belongs to exactly one `Doctor`. Because the `Doctor` row already exists in the database, it serves as the synchronization anchor:

```java
// Inside AppointmentService.java (Isolation = READ_COMMITTED)
Doctor doctor = doctorRepository.findByIdWithLock(request.doctorId())
        .orElseThrow(() -> new ResourceNotFoundException("Doctor", "id", request.doctorId()));
```

In `DoctorRepository.java`:
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT d FROM Doctor d WHERE d.id = :id")
Optional<Doctor> findByIdWithLock(@Param("id") Long id);
```

### 2.2 Execution Flow Under the Lock

When executed against MySQL, this generates:
```sql
SELECT id, specialization, consultation_fee, ... 
FROM doctors 
WHERE id = ? 
FOR UPDATE;
```

While the exclusive lock is held on the doctor's primary key:
1. Any concurrent transaction attempting to book or reschedule an appointment for that same doctor executes `SELECT ... FOR UPDATE` and **blocks** until the first transaction commits or rolls back.
2. The active transaction queries existing overlapping appointments:
   ```sql
   SELECT a FROM Appointment a 
   WHERE a.doctor.id = :doctorId 
     AND a.status IN ('BOOKED', 'CHECKED_IN', 'IN_CONSULTATION')
     AND a.startDateTime < :requestedEnd 
     AND a.endDateTime > :requestedStart
   ```
   *Mathematical Invariant:* Two time intervals `[S1, E1)` and `[S2, E2)` overlap if and only if `S1 < E2 AND E1 > S2`.
3. If any overlapping appointment or approved doctor leave exists, the transaction throws `AppointmentConflictException` (mapped to HTTP `409 Conflict`).
4. If no conflict exists, the appointment is inserted with status `BOOKED`, and the transaction commits.
5. The blocked concurrent transaction now unblocks, acquires the lock, executes step 2, observes the newly committed appointment, and is immediately rejected with an `AppointmentConflictException`.

### 2.3 Patient-Level Double Booking Prevention
In addition to locking the doctor, CareFlow also locks the patient:
```java
Patient patient = patientRepository.findByIdWithLock(request.patientId());
```
This guarantees that a patient cannot concurrently book appointments with two different doctors at the same time.

---

## 3. Outpatient Queue Number Concurrency

Section 11 of the system requirements specifies:
> *"Queue numbers must be allocated safely when multiple patients check in simultaneously. Use appropriate database locking or atomic allocation. Do not generate queue numbers by simply reading the current row count and adding one."*

### Strategy:
1. CareFlow maintains a dedicated sequence table: `doctor_daily_queue_sequence` with primary key `(doctor_id, queue_date)`.
2. A unique composite constraint exists on `queue_entries`: `CONSTRAINT uk_queue_doctor_date_num UNIQUE (doctor_id, queue_date, queue_number)`.
3. When checking in, CareFlow acquires a pessimistic lock on the `Doctor` record for that check-in transaction.
4. It reads the current `last_queue_number` for `(doctor_id, today)`. If no row exists, it inserts `1`. If a row exists, it increments `last_queue_number + 1` and saves.
5. Because check-in is serialized on the doctor entity, queue numbers are guaranteed to be **gapless, strictly sequential (1, 2, 3...), and completely free of duplicate key collisions**.

---

## 4. Race Condition Scenarios Handled

| Scenario | Behavior Under High Concurrency |
|---|---|
| **Two patients book the same slot simultaneously** | Thread A acquires Doctor lock, checks overlap (0), inserts booking, commits. Thread B unblocks, checks overlap (finds Thread A's booking), throws `AppointmentConflictException` (409 Conflict). |
| **Two patients book overlapping intervals (e.g. 10:00-10:30 and 10:15-10:45)** | Overlap query `S1 < E2 AND E1 > S2` catches the partial collision under the lock. Exactly 1 succeeds, the second is rejected. |
| **Cancellation racing with a new booking** | If cancellation commits first, the slot status becomes `CANCELLED` (which no longer blocks). The new booking query filters `status != 'CANCELLED'` and successfully books the released slot. |
| **Rescheduling racing with another booking** | Rescheduling executes inside the same pessimistic transaction boundary: locks the doctor, verifies the new slot is free, updates timestamps, and releases the old slot atomically. |
| **Concurrent patient check-ins** | Both patients receive distinct, ascending queue numbers without any gap or primary key violation. |

---

## 5. Automated Concurrency Test Evidence

The concurrency implementation is validated by automated multi-threaded tests in `AppointmentBookingConcurrencyTest.java` running against real database transactions:

### Test 1: `shouldPreventDoubleBookingUnderConcurrentRequests`
- **Setup:** 2 concurrent worker threads using `CountDownLatch` synchronization firing simultaneously for Dr. Smith (`DOC-2026-0001`) on the exact same 30-minute interval.
- **Result:**
  - `successCount` = 1
  - `conflictCount` = 1
  - `exceptions` = empty (clean domain exception handling)
  - `appointmentRepository.findConflictingDoctorAppointments(...)` = exactly 1 record in database.

### Test 2: `shouldAllocateUniqueQueueNumbersUnderConcurrentCheckIn`
- **Setup:** 2 concurrent worker threads checking in different patients for the same doctor on the current date simultaneously.
- **Result:**
  - Both requests succeeded.
  - Assigned queue numbers: `[1, 2]`.
  - Zero duplicate numbers, zero SQL constraint violations.

---

## 6. Architectural Trade-offs & Future Scaling

| Approach | Pros | Cons / Limitations |
|---|---|---|
| **Pessimistic Lock on Doctor (Current)** | Highly reliable, 100% consistency, zero phantom reads, supports arbitrary slot durations, very clean code. | Serializes bookings for the same doctor. If 1,000 requests target the same doctor in the same second, throughput is bounded by transaction execution time (~5-10ms per lock). |
| **Materialized Slot Tables** | Simple unique constraint `(doctor_id, slot_time)`. | Inflexible for variable duration appointments or emergency walk-ins; requires scheduled pre-generation of future slot rows. |
| **Distributed Lock (Redis / Redlock)** | Offloads lock contention from MySQL; highly scalable across distributed microservices. | Introduces Redis dependency, clock drift edge cases, and network partition failure modes. |
