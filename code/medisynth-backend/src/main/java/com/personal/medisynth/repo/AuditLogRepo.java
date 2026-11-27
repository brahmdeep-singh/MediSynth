package com.personal.medisynth.repo;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.personal.medisynth.model.AuditAction;
import com.personal.medisynth.model.AuditLog;

@Repository
public interface AuditLogRepo extends JpaRepository<AuditLog, Long> {

    long countByAction(AuditAction action);

    Page<AuditLog> findAllBy(Pageable pageable);
}
