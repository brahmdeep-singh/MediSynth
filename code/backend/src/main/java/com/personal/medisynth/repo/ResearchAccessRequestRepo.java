package com.personal.medisynth.repo;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.personal.medisynth.model.*;

import jakarta.persistence.LockModeType;

@Repository
public interface ResearchAccessRequestRepo extends JpaRepository<ResearchAccessRequest, Long> {

    boolean existsByResearcherIdAndDatasetIdAndStatus(Long researcherId, Long datasetId, RequestStatus status);

    boolean existsByResearcherIdAndDatasetIdAndStatusIn(Long researcherId, Long datasetId, Collection<RequestStatus> statuses);

    List<ResearchAccessRequest> findByResearcherIdAndDatasetIsNotNull(Long researcherId);

    long countByResearcherIdAndStatus(Long researcherId, RequestStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ResearchAccessRequest r where r.id = :id")
    Optional<ResearchAccessRequest> findByIdForUpdate(@Param("id") Long id);

    @EntityGraph(attributePaths = {"researcher", "reviewedBy"})
    Page<ResearchAccessRequest> findByResearcherId(Long researcherId, Pageable pageable);

    @EntityGraph(attributePaths = {"researcher", "reviewedBy"})
    Page<ResearchAccessRequest> findByStatus(RequestStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"researcher", "reviewedBy"})
    Page<ResearchAccessRequest> findAllBy(Pageable pageable);
}
