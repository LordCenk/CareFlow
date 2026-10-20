package com.careflow.queue.entity;

import com.careflow.appointment.entity.Appointment;
import com.careflow.doctor.entity.Doctor;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "queue_entries", uniqueConstraints = {
        @UniqueConstraint(name = "uk_queue_doctor_date_num", columnNames = {"doctor_id", "queue_date", "queue_number"})
})
public class QueueEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appointment_id", nullable = false, unique = true)
    private Appointment appointment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    @Column(name = "queue_date", nullable = false)
    private LocalDate queueDate;

    @Column(name = "queue_number", nullable = false)
    private int queueNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private QueueStatus status = QueueStatus.WAITING;

    @Column(name = "checked_in_at", nullable = false)
    private Instant checkedInAt;

    @Column(name = "called_at")
    private Instant calledAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    public QueueEntry() {
    }

    public QueueEntry(Appointment appointment, Doctor doctor, LocalDate queueDate, int queueNumber) {
        this.appointment = appointment;
        this.doctor = doctor;
        this.queueDate = queueDate;
        this.queueNumber = queueNumber;
        this.status = QueueStatus.WAITING;
        this.checkedInAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Appointment getAppointment() {
        return appointment;
    }

    public void setAppointment(Appointment appointment) {
        this.appointment = appointment;
    }

    public Doctor getDoctor() {
        return doctor;
    }

    public void setDoctor(Doctor doctor) {
        this.doctor = doctor;
    }

    public LocalDate getQueueDate() {
        return queueDate;
    }

    public void setQueueDate(LocalDate queueDate) {
        this.queueDate = queueDate;
    }

    public int getQueueNumber() {
        return queueNumber;
    }

    public void setQueueNumber(int queueNumber) {
        this.queueNumber = queueNumber;
    }

    public QueueStatus getStatus() {
        return status;
    }

    public void setStatus(QueueStatus status) {
        this.status = status;
    }

    public Instant getCheckedInAt() {
        return checkedInAt;
    }

    public void setCheckedInAt(Instant checkedInAt) {
        this.checkedInAt = checkedInAt;
    }

    public Instant getCalledAt() {
        return calledAt;
    }

    public void setCalledAt(Instant calledAt) {
        this.calledAt = calledAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }
}
