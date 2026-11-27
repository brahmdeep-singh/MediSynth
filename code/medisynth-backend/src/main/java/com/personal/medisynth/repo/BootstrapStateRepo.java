package com.personal.medisynth.repo;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.personal.medisynth.model.BootstrapState;

import jakarta.persistence.LockModeType;

@Repository
public interface BootstrapStateRepo extends JpaRepository<BootstrapState, Integer> {

    /** SELECT ... FOR UPDATE on the single state row: concurrent bootstraps queue up behind each other. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from BootstrapState b where b.id = 1")
    Optional<BootstrapState> lockState();
}
