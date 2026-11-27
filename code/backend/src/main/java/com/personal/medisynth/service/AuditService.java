package com.personal.medisynth.service;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.personal.medisynth.model.AuditAction;
import com.personal.medisynth.model.AuditLog;
import com.personal.medisynth.repo.AuditLogRepo;

/**
 * Callers must pass SAFE metadata only (ids, status names, reason codes). Never passwords, tokens or secrets.
 */
@Service
public class AuditService {

    private final AuditLogRepo auditLogRepo;

    public AuditService(AuditLogRepo auditLogRepo) {
        this.auditLogRepo = auditLogRepo;
    }

    /** Joins the caller's transaction: the audit row commits or rolls back together with the action. */
    @Transactional
    public void record(Long actorId, AuditAction action, String targetType, Long targetId, String metadata) {
        auditLogRepo.save(build(actorId, action, targetType, targetId, metadata));
    }

    /** Own transaction: the row survives even if the caller then fails/rolls back (used for failed attempts). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordIndependent(Long actorId, AuditAction action, String targetType, Long targetId,
            String metadata) {
        auditLogRepo.save(build(actorId, action, targetType, targetId, metadata));
    }

    private static AuditLog build(Long actorId, AuditAction action, String targetType, Long targetId,
            String metadata) {
        AuditLog log = new AuditLog();
        log.setActorId(actorId);
        log.setAction(action);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setMetadata(metadata == null ? null : (metadata.length() > 1000 ? metadata.substring(0, 1000) : metadata));
        log.setOccurredAt(Instant.now());
        return log;
    }
}
