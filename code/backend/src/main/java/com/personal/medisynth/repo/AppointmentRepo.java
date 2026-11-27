package com.personal.medisynth.repo;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.personal.medisynth.model.Appointment;
import com.personal.medisynth.model.AppointmentStatus;

import jakarta.persistence.LockModeType;

@Repository
public interface AppointmentRepo extends JpaRepository<Appointment, Long> {

    boolean existsByDoctorIdAndStartTimeAndStatusIn(Long doctorId, Instant startTime, Collection<AppointmentStatus> statuses);

    boolean existsByPatientIdAndStartTimeAndStatusIn(Long patientId, Instant startTime, Collection<AppointmentStatus> statuses);

    @EntityGraph(attributePaths = {"patient", "doctor", "doctor.user"})
    Optional<Appointment> findDetailedById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Appointment a where a.id = :id")
    Optional<Appointment> findByIdForUpdate(@Param("id") Long id);

    @EntityGraph(attributePaths = {"patient", "doctor", "doctor.user"})
    Page<Appointment> findByPatientId(Long patientId, Pageable pageable);

    @EntityGraph(attributePaths = {"patient", "doctor", "doctor.user"})
    Page<Appointment> findByPatientIdAndStatus(Long patientId, AppointmentStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"patient", "doctor", "doctor.user"})
    Page<Appointment> findByDoctorUserId(Long doctorUserId, Pageable pageable);

    @EntityGraph(attributePaths = {"patient", "doctor", "doctor.user"})
    Page<Appointment> findByDoctorUserIdAndStatus(Long doctorUserId, AppointmentStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"patient", "doctor", "doctor.user"})
    Page<Appointment> findAllBy(Pageable pageable);

    @EntityGraph(attributePaths = {"patient", "doctor", "doctor.user"})
    Page<Appointment> findByStatus(AppointmentStatus status, Pageable pageable);
}
