package com.personal.medisynth.DTO;

import java.time.Instant;

import com.personal.medisynth.model.AuditAction;

public record AuditLogResponse(Long id, Long actorId, String actorName, AuditAction action, String targetType,
        Long targetId, String metadata, Instant occurredAt) {
}
