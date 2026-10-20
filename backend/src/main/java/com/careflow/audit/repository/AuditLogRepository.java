package com.careflow.audit.repository;

import com.careflow.audit.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    Page<AuditLog> findByEntityName(String entityName, Pageable pageable);
    List<AuditLog> findByEntityNameAndEntityId(String entityName, Long entityId);
}
