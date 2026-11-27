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
public interface LabTestRequestRepo extends JpaRepository<LabTestRequest, Long> {

    /** Row lock so two technicians cannot start/finish the same test at the same time. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from LabTestRequest t where t.id = :id")
    Optional<LabTestRequest> findByIdForUpdate(@Param("id") Long id);

    @EntityGraph(attributePaths = {"patient", "orderedBy", "processedBy"})
    Page<LabTestRequest> findByPatientId(Long patientId, Pageable pageable);

    @EntityGraph(attributePaths = {"patient", "orderedBy", "processedBy"})
    Page<LabTestRequest> findByPatientIdAndStatus(Long patientId, LabTestStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"patient", "orderedBy", "processedBy"})
    Page<LabTestRequest> findByOrderedById(Long doctorUserId, Pageable pageable);

    @EntityGraph(attributePaths = {"patient", "orderedBy", "processedBy"})
    Page<LabTestRequest> findByOrderedByIdAndStatus(Long doctorUserId, LabTestStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"patient", "orderedBy", "processedBy"})
    Page<LabTestRequest> findByStatus(LabTestStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"patient", "orderedBy", "processedBy"})
    Page<LabTestRequest> findAllBy(Pageable pageable);
}
