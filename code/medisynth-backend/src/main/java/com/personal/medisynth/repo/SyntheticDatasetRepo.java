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

import com.personal.medisynth.model.DatasetStatus;
import com.personal.medisynth.model.SyntheticDataset;

import jakarta.persistence.LockModeType;

@Repository
public interface SyntheticDatasetRepo extends JpaRepository<SyntheticDataset, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from SyntheticDataset d where d.id = :id")
    Optional<SyntheticDataset> findByIdForUpdate(@Param("id") Long id);

    @EntityGraph(attributePaths = {"createdBy"})
    Page<SyntheticDataset> findAllBy(Pageable pageable);

    @EntityGraph(attributePaths = {"createdBy"})
    Page<SyntheticDataset> findByStatus(DatasetStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"createdBy"})
    Optional<SyntheticDataset> findWithCreatorById(Long id);

    long countByStatus(DatasetStatus status);
}
