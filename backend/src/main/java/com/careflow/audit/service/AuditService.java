package com.careflow.audit.service;

import com.careflow.audit.entity.AuditLog;
import com.careflow.audit.repository.AuditLogRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordAudit(String entityName, Long entityId, String action, String performedBy, String details) {
        AuditLog log = new AuditLog(entityName, entityId, action, performedBy, details);
        auditLogRepository.save(log);
    }
}
