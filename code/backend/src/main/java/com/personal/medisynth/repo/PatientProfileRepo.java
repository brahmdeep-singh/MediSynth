package com.personal.medisynth.repo;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.personal.medisynth.model.PatientProfile;

@Repository
public interface PatientProfileRepo extends JpaRepository<PatientProfile, Long> {

    Optional<PatientProfile> findByUserId(Long userId);
}
