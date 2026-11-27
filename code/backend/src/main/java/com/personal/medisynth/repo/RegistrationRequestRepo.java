package com.personal.medisynth.repo;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.personal.medisynth.model.RegistrationRequest;
import com.personal.medisynth.model.RequestStatus;
import com.personal.medisynth.model.Role;

import jakarta.persistence.LockModeType;

@Repository
public interface RegistrationRequestRepo extends JpaRepository<RegistrationRequest, Long> {

    boolean existsByUserIdAndStatus(Long userId, RequestStatus status);

    Optional<RegistrationRequest> findByStatusTokenHash(String statusTokenHash);

    /** Row lock so two admins cannot decide the same request at the same time. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RegistrationRequest r where r.id = :id")
    Optional<RegistrationRequest> findByIdForUpdate(@Param("id") Long id);

    @EntityGraph(attributePaths = {"user", "reviewedBy"})
    Optional<RegistrationRequest> findDetailedById(Long id);

    @EntityGraph(attributePaths = {"user", "reviewedBy"})
    Page<RegistrationRequest> findAllBy(Pageable pageable);

    @EntityGraph(attributePaths = {"user", "reviewedBy"})
    Page<RegistrationRequest> findByStatus(RequestStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"user", "reviewedBy"})
    Page<RegistrationRequest> findByRequestedRole(Role requestedRole, Pageable pageable);

    @EntityGraph(attributePaths = {"user", "reviewedBy"})
    Page<RegistrationRequest> findByStatusAndRequestedRole(RequestStatus status, Role requestedRole, Pageable pageable);
}
