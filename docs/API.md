# CareFlow — REST API Reference Documentation

All API endpoints are prefixed with `/api/v1`. Authentication is handled via stateless **JWT Bearer Tokens** passed in the `Authorization` HTTP header:
```http
Authorization: Bearer <your_jwt_token>
```

Swagger UI Interactive Explorer is available at:
`http://localhost:8080/swagger-ui/index.html` (or `http://localhost:8080/swagger-ui.html`)
OpenAPI Specification JSON: `http://localhost:8080/v3/api-docs`

---

## 1. Unified Response Structure

### 1.1 Success Response (`ApiResponse<T>`)
```json
{
  "success": true,
  "message": "Operation successful",
  "data": { ... },
  "timestamp": "2026-10-21T00:15:00.000Z"
}
```

### 1.2 Error Response (`ErrorResponse`)
```json
{
  "timestamp": "2026-10-21T00:15:00.000Z",
  "status": 409,
  "error": "Conflict",
  "message": "The requested appointment slot is already booked or overlaps with an existing appointment",
  "path": "/api/v1/appointments",
  "validationErrors": null
}
```

---

## 2. Endpoint Summary Table

| Category | HTTP Method | Endpoint | Allowed Roles | Description |
|---|---|---|---|---|
| **Auth** | `POST` | `/api/v1/auth/register` | Public | Register a new user / patient account |
| **Auth** | `POST` | `/api/v1/auth/login` | Public | Authenticate with email & password |
| **Users** | `GET` | `/api/v1/users/me` | Authenticated | Get current authenticated user profile |
| **Patients** | `POST` | `/api/v1/patients` | `ADMIN`, `RECEPTIONIST` | Register walk-in or staff-created patient |
| **Patients** | `GET` | `/api/v1/patients/{id}` | All Roles (Ownership enforced) | Get patient profile details |
| **Patients** | `PUT` | `/api/v1/patients/{id}` | All Roles (Ownership enforced) | Update patient contact details |
| **Patients** | `GET` | `/api/v1/patients` | `ADMIN`, `RECEPTIONIST` | List all patients (paginated) |
| **Departments** | `GET` | `/api/v1/departments` | Public / All | List hospital departments |
| **Departments** | `POST` | `/api/v1/departments` | `ADMIN` | Create a clinical department |
| **Departments** | `PUT` | `/api/v1/departments/{id}` | `ADMIN` | Update department details |
| **Doctors** | `GET` | `/api/v1/doctors` | Public / All | Search active doctors by department/specialty |
| **Doctors** | `GET` | `/api/v1/doctors/{id}` | Public / All | Get doctor profile and consultation fee |
| **Doctors** | `POST` | `/api/v1/doctors` | `ADMIN` | Register new doctor profile |
| **Doctors** | `PUT` | `/api/v1/doctors/{id}` | `ADMIN` | Update doctor details or fee |
| **Scheduling** | `GET` | `/api/v1/doctors/{id}/availability` | All | Get doctor's recurring weekly hours |
| **Scheduling** | `POST` | `/api/v1/doctors/{id}/availability` | `ADMIN` | Configure recurring working hours |
| **Scheduling** | `GET` | `/api/v1/doctors/{id}/leaves` | All | Get scheduled doctor leaves |
| **Scheduling** | `POST` | `/api/v1/doctors/{id}/leaves` | `ADMIN`, `DOCTOR` | Schedule doctor absence/leave |
| **Scheduling** | `GET` | `/api/v1/appointments/available-slots` | All | Query real-time available appointment slots |
| **Appointments**| `POST` | `/api/v1/appointments` | `PATIENT`, `RECEPTIONIST`, `ADMIN` | Book an appointment slot |
| **Appointments**| `GET` | `/api/v1/appointments/{id}` | All (Ownership enforced) | Get appointment details |
| **Appointments**| `GET` | `/api/v1/appointments` | All (Filtered by role) | List appointments (paginated) |
| **Appointments**| `POST` | `/api/v1/appointments/{id}/reschedule` | `PATIENT`, `RECEPTIONIST`, `ADMIN` | Reschedule an existing appointment |
| **Appointments**| `POST` | `/api/v1/appointments/{id}/cancel` | All (Ownership enforced) | Cancel an appointment with reason |
| **Appointments**| `PATCH` | `/api/v1/appointments/{id}/status` | `DOCTOR`, `RECEPTIONIST`, `ADMIN` | Update status via state machine |
| **Queue** | `POST` | `/api/v1/queue/check-in` | `RECEPTIONIST`, `ADMIN` | Check in patient and assign queue # |
| **Queue** | `GET` | `/api/v1/queue` | `DOCTOR`, `RECEPTIONIST`, `ADMIN` | View doctor's daily outpatient queue |
| **Queue** | `POST` | `/api/v1/queue/{id}/call` | `DOCTOR`, `RECEPTIONIST`, `ADMIN` | Call next waiting patient |
| **Queue** | `POST` | `/api/v1/queue/{id}/start-consultation` | `DOCTOR`, `ADMIN` | Mark consultation in progress |
| **Queue** | `POST` | `/api/v1/queue/{id}/complete` | `DOCTOR`, `ADMIN` | Mark consultation completed |
| **Queue** | `POST` | `/api/v1/queue/{id}/no-show` | `DOCTOR`, `RECEPTIONIST`, `ADMIN` | Mark patient as no-show |
| **Billing** | `POST` | `/api/v1/invoices/generate/{apptId}`| `RECEPTIONIST`, `ADMIN` | Generate invoice from doctor fee |
| **Billing** | `GET` | `/api/v1/invoices/{id}` | `PATIENT`, `RECEPTIONIST`, `ADMIN` | Get invoice details |
| **Billing** | `GET` | `/api/v1/invoices` | All (Filtered by role) | List invoices (paginated) |
| **Billing** | `POST` | `/api/v1/invoices/{id}/payments` | All (Authorized payers) | Process simulated payment |
| **Billing** | `POST` | `/api/v1/invoices/{id}/void` | `ADMIN` | Void an unpaid invoice |

---

## 3. Sample Requests & Responses

### 3.1 Authentication — Login
`POST /api/v1/auth/login`
```json
{
  "email": "admin@careflow.com",
  "password": "password123"
}
```
**Response (200 OK):**
```json
{
  "success": true,
  "message": "Login successful",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9...",
    "tokenType": "Bearer",
    "userId": 1,
    "name": "System Administrator",
    "email": "admin@careflow.com",
    "role": "ADMIN",
    "patientId": null,
    "doctorId": null,
    "expiresInMs": 86400000
  },
  "timestamp": "2026-10-21T00:20:00Z"
}
```

### 3.2 Query Available Appointment Slots
`GET /api/v1/appointments/available-slots?doctorId=1&date=2026-11-09`

**Response (200 OK):**
```json
{
  "success": true,
  "message": "Operation successful",
  "data": [
    {
      "startDateTime": "2026-11-09T09:00:00",
      "endDateTime": "2026-11-09T09:30:00",
      "durationMinutes": 30,
      "available": true
    },
    {
      "startDateTime": "2026-11-09T09:30:00",
      "endDateTime": "2026-11-09T10:00:00",
      "durationMinutes": 30,
      "available": false
    }
  ],
  "timestamp": "2026-10-21T00:20:00Z"
}
```

### 3.3 Book an Appointment
`POST /api/v1/appointments`
```json
{
  "patientId": 1,
  "doctorId": 1,
  "startDateTime": "2026-11-09T09:00:00",
  "endDateTime": "2026-11-09T09:30:00",
  "reasonForVisit": "Cardiovascular checkup and ECG review"
}
```
**Response (201 Created):**
```json
{
  "success": true,
  "message": "Appointment booked successfully",
  "data": {
    "id": 1,
    "appointmentReference": "APT-2026-A1B2C3D4",
    "patientId": 1,
    "patientName": "Alice Walker",
    "patientContact": "+1-555-0101",
    "doctorId": 1,
    "doctorName": "Dr. John Smith",
    "doctorSpecialization": "Cardiologist",
    "startDateTime": "2026-11-09T09:00:00",
    "endDateTime": "2026-11-09T09:30:00",
    "status": "BOOKED",
    "reasonForVisit": "Cardiovascular checkup and ECG review",
    "cancellationReason": null,
    "createdBy": "patient.alice@careflow.com",
    "createdAt": "2026-10-21T00:20:00Z",
    "updatedAt": "2026-10-21T00:20:00Z"
  },
  "timestamp": "2026-10-21T00:20:00Z"
}
```

### 3.4 Outpatient Queue Check-In
`POST /api/v1/queue/check-in`
```json
{
  "appointmentId": 1
}
```
**Response (201 Created):**
```json
{
  "success": true,
  "message": "Patient checked in successfully",
  "data": {
    "id": 1,
    "appointmentId": 1,
    "appointmentReference": "APT-2026-A1B2C3D4",
    "patientId": 1,
    "patientName": "Alice Walker",
    "doctorId": 1,
    "doctorName": "Dr. John Smith",
    "queueDate": "2026-10-21",
    "queueNumber": 1,
    "status": "WAITING",
    "checkedInAt": "2026-10-21T08:50:00Z",
    "calledAt": null,
    "completedAt": null
  },
  "timestamp": "2026-10-21T08:50:00Z"
}
```

### 3.5 Generate and Settle Invoice
`POST /api/v1/invoices/generate/1`

**Response (201 Created):**
```json
{
  "success": true,
  "message": "Invoice generated successfully",
  "data": {
    "id": 1,
    "invoiceReference": "INV-2026-7890ABCD",
    "appointmentId": 1,
    "appointmentReference": "APT-2026-A1B2C3D4",
    "patientId": 1,
    "patientName": "Alice Walker",
    "amount": 75.00,
    "currency": "USD",
    "status": "ISSUED",
    "issuedAt": "2026-10-21T09:35:00Z",
    "paidAt": null
  },
  "timestamp": "2026-10-21T09:35:00Z"
}
```

`POST /api/v1/invoices/1/payments`
```json
{
  "method": "CREDIT_CARD"
}
```
**Response (201 Created):**
```json
{
  "success": true,
  "message": "Payment processed successfully",
  "data": {
    "id": 1,
    "invoiceId": 1,
    "paymentReference": "PAY-2026-E4F5G6H7",
    "amount": 75.00,
    "method": "CREDIT_CARD",
    "status": "SUCCESS",
    "createdAt": "2026-10-21T09:36:00Z"
  },
  "timestamp": "2026-10-21T09:36:00Z"
}
```
