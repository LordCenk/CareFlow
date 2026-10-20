package com.careflow.notification.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recipient_email", nullable = false, length = 150)
    private String recipientEmail;

    @Column(name = "recipient_name", length = 120)
    private String recipientName;

    @Column(name = "notification_type", nullable = false, length = 50)
    private String notificationType;

    @Column(nullable = false, length = 255)
    private String subject;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(nullable = false, length = 20)
    private String status = "PENDING";

    @Column(name = "appointment_id")
    private Long appointmentId;

    @Column(name = "scheduled_for", nullable = false)
    private LocalDateTime scheduledFor;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Notification() {
    }

    public Notification(
            String recipientEmail,
            String recipientName,
            String notificationType,
            String subject,
            String message,
            Long appointmentId,
            LocalDateTime scheduledFor
    ) {
        this.recipientEmail = recipientEmail;
        this.recipientName = recipientName;
        this.notificationType = notificationType;
        this.subject = subject;
        this.message = message;
        this.appointmentId = appointmentId;
        this.scheduledFor = scheduledFor != null ? scheduledFor : LocalDateTime.now();
        this.status = "PENDING";
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRecipientEmail() {
        return recipientEmail;
    }

    public void setRecipientEmail(String recipientEmail) {
        this.recipientEmail = recipientEmail;
    }

    public String getRecipientName() {
        return recipientName;
    }

    public void setRecipientName(String recipientName) {
        this.recipientName = recipientName;
    }

    public String getNotificationType() {
        return notificationType;
    }

    public void setNotificationType(String notificationType) {
        this.notificationType = notificationType;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getAppointmentId() {
        return appointmentId;
    }

    public void setAppointmentId(Long appointmentId) {
        this.appointmentId = appointmentId;
    }

    public LocalDateTime getScheduledFor() {
        return scheduledFor;
    }

    public void setScheduledFor(LocalDateTime scheduledFor) {
        this.scheduledFor = scheduledFor;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public void setSentAt(Instant sentAt) {
        this.sentAt = sentAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
