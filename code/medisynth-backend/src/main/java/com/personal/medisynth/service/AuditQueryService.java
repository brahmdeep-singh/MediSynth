package com.personal.medisynth.service;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.personal.medisynth.DTO.AuditLogResponse;
import com.personal.medisynth.DTO.PageResponse;
import com.personal.medisynth.model.AuditLog;
import com.personal.medisynth.model.Users;
import com.personal.medisynth.repo.AuditLogRepo;
import com.personal.medisynth.repo.UserRepo;

/** Read-only view of the audit trail for admins. Metadata is already safe (ids and status names only). */
@Service
public class AuditQueryService {

    private final AuditLogRepo auditLogRepo;
    private final UserRepo userRepo;

    public AuditQueryService(AuditLogRepo auditLogRepo, UserRepo userRepo) {
        this.auditLogRepo = auditLogRepo;
        this.userRepo = userRepo;
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> list(int page, int size) {
        Pageable p = AdminService.pageable(page, size, Sort.by(Sort.Direction.DESC, "occurredAt"));
        Page<AuditLog> result = auditLogRepo.findAllBy(p);
        Set<Long> actorIds = result.getContent().stream().map(AuditLog::getActorId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> names = new HashMap<>();
        for (Users u : userRepo.findAllById(actorIds)) {
            names.put(u.getId(), u.getName());
        }
        return PageResponse.of(result.map(a -> new AuditLogResponse(a.getId(), a.getActorId(),
                a.getActorId() == null ? null : names.get(a.getActorId()), a.getAction(), a.getTargetType(),
                a.getTargetId(), a.getMetadata(), a.getOccurredAt())));
    }
}
