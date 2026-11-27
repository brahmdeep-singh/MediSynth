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
public interface ResearcherProfileRepo extends JpaRepository<ResearcherProfile, Long> {

    Optional<ResearcherProfile> findByUserId(Long userId);

    List<ResearcherProfile> findByUserIdIn(Collection<Long> userIds);
}
