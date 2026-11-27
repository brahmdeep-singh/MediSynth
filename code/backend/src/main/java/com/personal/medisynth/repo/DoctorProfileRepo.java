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

import com.personal.medisynth.model.AccountStatus;
import com.personal.medisynth.model.DoctorProfile;
import com.personal.medisynth.model.VerificationStatus;

import jakarta.persistence.LockModeType;

@Repository
public interface DoctorProfileRepo extends JpaRepository<DoctorProfile, Long> {

    boolean existsByLicenseNumber(String licenseNumber);

    @EntityGraph(attributePaths = "user")
    Optional<DoctorProfile> findByUserId(Long userId);

    @EntityGraph(attributePaths = "user")
    List<DoctorProfile> findByUserIdIn(Collection<Long> userIds);

    @EntityGraph(attributePaths = "user")
    Optional<DoctorProfile> findDetailedById(Long id);

    /** Serialises concurrent bookings for the same doctor before the slot check. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from DoctorProfile d where d.id = :id")
    Optional<DoctorProfile> findByIdForUpdate(@Param("id") Long id);

    @EntityGraph(attributePaths = "user")
    Page<DoctorProfile> findAllBy(Pageable pageable);

    @EntityGraph(attributePaths = "user")
    Page<DoctorProfile> findByVerificationStatus(VerificationStatus status, Pageable pageable);

    @EntityGraph(attributePaths = "user")
    Page<DoctorProfile> findByVerificationStatusAndUserAccountStatus(
            VerificationStatus verification, AccountStatus accountStatus, Pageable pageable);

    @EntityGraph(attributePaths = "user")
    Page<DoctorProfile> findByVerificationStatusAndUserAccountStatusAndSpecializationContainingIgnoreCase(
            VerificationStatus verification, AccountStatus accountStatus, String specialization, Pageable pageable);
}
