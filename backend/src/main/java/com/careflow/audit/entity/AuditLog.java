package com.careflow.audit.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entity_name", nullable = false, length = 50)
    private String entityName;

    @Column(name = "entity_id")
    private Long entityId;

    @Column(nullable = false, length = 50)
    private String action;

    @Column(name = "performed_by", nullable = false, length = 150)
    private String performedBy;

    @Column(nullable = false, updatable = false)
    private Instant timestamp;

    @Column(columnDefinition = "TEXT")
    private String details;

    public AuditLog() {
    }

    public AuditLog(String entityName, Long entityId, String action, String performedBy, String details) {
        this.entityName = entityName;
        this.entityId = entityId;
        this.action = action;
        this.performedBy = performedBy;
        this.details = details;
    }

    @PrePersist
    protected void onCreate() {
        this.timestamp = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getEntityName() {
        return entityName;
    }

    public Long getEntityId() {
        return entityId;
    }

    public String getAction() {
        return action;
    }

    public String getPerformedBy() {
        return performedBy;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getDetails() {
        return details;
    }
}
