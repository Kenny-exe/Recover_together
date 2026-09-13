package com.recovertogether.backend.repository;

import com.recovertogether.backend.entity.AuditLog;
import com.recovertogether.backend.enums.AuditAction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<AuditLog> findByActionOrderByCreatedAtDesc(AuditAction action);

    List<AuditLog> findAllByOrderByCreatedAtDesc();
}
