package com.personal.medisynth.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.personal.medisynth.DTO.AppointmentResponse;
import com.personal.medisynth.DTO.CreateAppointmentRequest;
import com.personal.medisynth.DTO.PageResponse;
import com.personal.medisynth.exception.ApiException;
import com.personal.medisynth.model.AccountStatus;
import com.personal.medisynth.model.Appointment;
import com.personal.medisynth.model.AppointmentStatus;
import com.personal.medisynth.model.AuditAction;
import com.personal.medisynth.model.DoctorProfile;
import com.personal.medisynth.model.Role;
import com.personal.medisynth.model.UserPrincipal;
import com.personal.medisynth.model.Users;
import com.personal.medisynth.model.VerificationStatus;
import com.personal.medisynth.repo.AppointmentRepo;
import com.personal.medisynth.repo.DoctorProfileRepo;
import com.personal.medisynth.repo.UserRepo;

/**
 * Appointment rules:
 * - Slots are 30 minutes, aligned to :00/:30 (UTC epoch, so correct in every half-hour time zone).
 * - Double booking is prevented twice: the doctor row is locked (SELECT ... FOR UPDATE) while the slot is
 *   checked, and partial unique indexes in PostgreSQL reject any duplicate that slips past.
 * - Visibility: patient = own, doctor = assigned to them, admin = all. Anyone else gets 404 (no existence leak).
 */
@Service
public class AppointmentService {

    private static final long SLOT_SECONDS = 30 * 60L;
    private static final Duration MAX_ADVANCE = Duration.ofDays(180);

    private final AppointmentRepo appointmentRepo;
    private final DoctorProfileRepo doctorProfileRepo;
    private final UserRepo userRepo;
    private final AuditService auditService;

    public AppointmentService(AppointmentRepo appointmentRepo, DoctorProfileRepo doctorProfileRepo, UserRepo userRepo,
            AuditService auditService) {
        this.appointmentRepo = appointmentRepo;
        this.doctorProfileRepo = doctorProfileRepo;
        this.userRepo = userRepo;
        this.auditService = auditService;
    }

    // ------------------------------------------------------------------ create

    @Transactional
    public AppointmentResponse create(Long patientUserId, CreateAppointmentRequest req) {
        Users patient = userRepo.findById(patientUserId)
                .orElseThrow(() -> ApiException.unauthorized("UNAUTHENTICATED", "Authentication is required"));
        if (patient.getGrantedRole() != Role.PATIENT || patient.getAccountStatus() != AccountStatus.ACTIVE) {
            throw ApiException.forbidden("FORBIDDEN", "Only active patients can book appointments");
        }

        Instant start = req.appointmentTime();
        if (start.getNano() != 0 || start.getEpochSecond() % SLOT_SECONDS != 0) {
            throw ApiException.badRequest("INVALID_SLOT",
                    "Appointments start on the hour or half hour (e.g. 10:00 or 10:30)");
        }
        Instant now = Instant.now();
        if (!start.isAfter(now)) {
            throw ApiException.badRequest("APPOINTMENT_IN_PAST", "The appointment time must be in the future");
        }
        if (start.isAfter(now.plus(MAX_ADVANCE))) {
            throw ApiException.badRequest("APPOINTMENT_TOO_FAR", "Appointments can be booked up to 180 days ahead");
        }

        // Lock the doctor row: concurrent bookings for the same doctor are queued here.
        DoctorProfile doctor = doctorProfileRepo.findByIdForUpdate(req.doctorId())
                .orElseThrow(() -> ApiException.notFound("DOCTOR_NOT_FOUND", "Doctor not found"));
        // Only approved (verified) + active doctors accept bookings. Others look non-existent.
        if (doctor.getVerificationStatus() != VerificationStatus.VERIFIED
                || doctor.getUser().getAccountStatus() != AccountStatus.ACTIVE) {
            throw ApiException.notFound("DOCTOR_NOT_FOUND", "Doctor not found");
        }

        if (appointmentRepo.existsByDoctorIdAndStartTimeAndStatusIn(doctor.getId(), start, AppointmentStatus.LIVE)) {
            throw ApiException.conflict("SLOT_UNAVAILABLE", "That time slot is no longer available");
        }
        if (appointmentRepo.existsByPatientIdAndStartTimeAndStatusIn(patient.getId(), start, AppointmentStatus.LIVE)) {
            throw ApiException.conflict("PATIENT_SLOT_CONFLICT", "You already have an appointment at that time");
        }

        Appointment appointment = new Appointment();
        appointment.setPatient(patient);
        appointment.setDoctor(doctor);
        appointment.setStartTime(start);
        appointment.setReason(InputNormalizer.text(req.reason()));
        appointment.setStatus(AppointmentStatus.PENDING);
        appointmentRepo.saveAndFlush(appointment); // unique index violations surface here as 409
        auditService.record(patientUserId, AuditAction.APPOINTMENT_CREATED, "APPOINTMENT", appointment.getId(),
                "doctorId=" + doctor.getId());
        return AppointmentResponse.from(appointment);
    }

    // ------------------------------------------------------------------ read

    @Transactional(readOnly = true)
    public PageResponse<AppointmentResponse> listMine(UserPrincipal caller, AppointmentStatus status, int page, int size) {
        Pageable p = AdminService.pageable(page, size, Sort.by(Sort.Direction.DESC, "startTime"));
        Page<Appointment> result;
        if (caller.getRole() == Role.PATIENT) {
            result = status == null ? appointmentRepo.findByPatientId(caller.getId(), p)
                    : appointmentRepo.findByPatientIdAndStatus(caller.getId(), status, p);
        } else if (caller.getRole() == Role.DOCTOR) {
            result = status == null ? appointmentRepo.findByDoctorUserId(caller.getId(), p)
                    : appointmentRepo.findByDoctorUserIdAndStatus(caller.getId(), status, p);
        } else {
            throw ApiException.forbidden("FORBIDDEN", "Administrators use /api/admin/appointments");
        }
        return PageResponse.of(result.map(AppointmentResponse::from));
    }

    @Transactional(readOnly = true)
    public AppointmentResponse getById(Long id, UserPrincipal caller) {
        Appointment a = appointmentRepo.findDetailedById(id).orElseThrow(AppointmentService::notFound);
        requireParticipantOrAdmin(a, caller);
        return AppointmentResponse.from(a);
    }

    // ------------------------------------------------------------------ change

    @Transactional
    public AppointmentResponse cancel(Long id, UserPrincipal caller, String reason) {
        return changeStatus(id, caller, AppointmentStatus.CANCELLED, reason);
    }

    @Transactional
    public AppointmentResponse changeStatus(Long id, UserPrincipal caller, AppointmentStatus target, String reason) {
        // Existence + access are decided on a non-locking read first so outsiders cannot probe or block rows.
        Appointment visible = appointmentRepo.findDetailedById(id).orElseThrow(AppointmentService::notFound);
        requireParticipantOrAdmin(visible, caller);

        Appointment a = appointmentRepo.findByIdForUpdate(id).orElseThrow(AppointmentService::notFound);
        AppointmentStatus current = a.getStatus();
        if (!allowedTransitions(current).contains(target)) {
            throw ApiException.conflict("INVALID_STATUS_TRANSITION",
                    "Cannot change an appointment from " + current + " to " + target);
        }

        Instant now = Instant.now();
        switch (caller.getRole()) {
            case PATIENT -> {
                if (target != AppointmentStatus.CANCELLED) {
                    throw ApiException.forbidden("ROLE_CANNOT_SET_STATUS", "Patients can only cancel their appointments");
                }
                requireFuture(a, now);
            }
            case DOCTOR -> {
                if (target == AppointmentStatus.CANCELLED) {
                    requireFuture(a, now);
                } else if (target == AppointmentStatus.COMPLETED && a.getStartTime().isAfter(now)) {
                    throw ApiException.conflict("APPOINTMENT_NOT_STARTED",
                            "An appointment can only be completed after its start time");
                }
            }
            case ADMIN -> {
                // Admins may apply any valid transition; the action is audited below.
            }
            default -> throw ApiException.forbidden("FORBIDDEN", "You do not have permission to perform this action");
        }

        a.setStatus(target);
        if (target == AppointmentStatus.CANCELLED) {
            a.setCancellationReason(InputNormalizer.blankToNull(reason));
        }
        auditService.record(caller.getId(),
                caller.getRole() == Role.ADMIN ? AuditAction.APPOINTMENT_STATUS_CHANGED_BY_ADMIN
                        : AuditAction.APPOINTMENT_STATUS_CHANGED,
                "APPOINTMENT", a.getId(), "from=" + current + ";to=" + target);
        return AppointmentResponse.from(a);
    }

    // ------------------------------------------------------------------ helpers

    private static Set<AppointmentStatus> allowedTransitions(AppointmentStatus from) {
        return switch (from) {
            case PENDING -> Set.of(AppointmentStatus.CONFIRMED, AppointmentStatus.CANCELLED);
            case CONFIRMED -> Set.of(AppointmentStatus.COMPLETED, AppointmentStatus.CANCELLED);
            case CANCELLED, COMPLETED -> Set.of();
        };
    }

    private static void requireFuture(Appointment a, Instant now) {
        if (!a.getStartTime().isAfter(now)) {
            throw ApiException.conflict("APPOINTMENT_ALREADY_STARTED", "This appointment has already started");
        }
    }

    /** IDOR guard: anyone who is not the patient, the assigned doctor, or an admin sees a plain 404. */
    private static void requireParticipantOrAdmin(Appointment a, UserPrincipal caller) {
        boolean allowed = switch (caller.getRole()) {
            case ADMIN -> true;
            case PATIENT -> a.getPatient().getId().equals(caller.getId());
            case DOCTOR -> a.getDoctor().getUser().getId().equals(caller.getId());
            default -> false; // lab technicians and researchers have no business with appointments
        };
        if (!allowed) {
            throw notFound();
        }
    }

    private static ApiException notFound() {
        return ApiException.notFound("APPOINTMENT_NOT_FOUND", "Appointment not found");
    }
}
