package com.personal.medisynth.service;

import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.personal.medisynth.DTO.LabTestResponse;
import com.personal.medisynth.DTO.OrderLabTestRequest;
import com.personal.medisynth.DTO.PageResponse;
import com.personal.medisynth.exception.ApiException;
import com.personal.medisynth.model.Appointment;
import com.personal.medisynth.model.AppointmentStatus;
import com.personal.medisynth.model.AuditAction;
import com.personal.medisynth.model.LabTestRequest;
import com.personal.medisynth.model.LabTestStatus;
import com.personal.medisynth.model.UserPrincipal;
import com.personal.medisynth.model.Users;
import com.personal.medisynth.repo.AppointmentRepo;
import com.personal.medisynth.repo.LabTestRequestRepo;
import com.personal.medisynth.repo.UserRepo;

/**
 * Lab test workflow: doctor orders (REQUESTED) -> technician starts (IN_PROGRESS) -> technician completes
 * (COMPLETED, result recorded). The ordering doctor may cancel while still REQUESTED.
 * Ownership is always checked here, in addition to the URL role rules in SecurityConfig.
 */
@Service
public class LabTestService {

    private final LabTestRequestRepo labRepo;
    private final AppointmentRepo appointmentRepo;
    private final UserRepo userRepo;
    private final AuditService auditService;

    public LabTestService(LabTestRequestRepo labRepo, AppointmentRepo appointmentRepo, UserRepo userRepo,
            AuditService auditService) {
        this.labRepo = labRepo;
        this.appointmentRepo = appointmentRepo;
        this.userRepo = userRepo;
        this.auditService = auditService;
    }

    // ------------------------------------------------------------------ doctor

    @Transactional
    public LabTestResponse order(Long doctorUserId, OrderLabTestRequest body) {
        Appointment appt = appointmentRepo.findDetailedById(body.appointmentId())
                .filter(a -> a.getDoctor().getUser().getId().equals(doctorUserId))
                // someone else's appointment looks exactly like a missing one (IDOR guard)
                .orElseThrow(() -> ApiException.notFound("APPOINTMENT_NOT_FOUND", "Appointment not found"));
        if (appt.getStatus() == AppointmentStatus.CANCELLED) {
            throw ApiException.conflict("APPOINTMENT_CANCELLED", "Tests cannot be ordered for a cancelled appointment");
        }
        LabTestRequest t = new LabTestRequest();
        t.setPatient(appt.getPatient());                  // patient comes from the appointment, never the client
        t.setOrderedBy(appt.getDoctor().getUser());
        t.setAppointment(appt);
        t.setTestType(InputNormalizer.text(body.testType()));
        t.setClinicalNotes(InputNormalizer.blankToNull(body.notes()));
        t.setStatus(LabTestStatus.REQUESTED);
        labRepo.saveAndFlush(t);
        auditService.record(doctorUserId, AuditAction.LAB_TEST_ORDERED, "LAB_TEST", t.getId(),
                "appointmentId=" + appt.getId() + ";patientId=" + appt.getPatient().getId());
        return LabTestResponse.from(t);
    }

    @Transactional
    public LabTestResponse cancel(Long id, Long doctorUserId) {
        LabTestRequest t = labRepo.findByIdForUpdate(id)
                .filter(x -> x.getOrderedBy().getId().equals(doctorUserId))
                .orElseThrow(() -> ApiException.notFound("LAB_TEST_NOT_FOUND", "Lab test not found"));
        if (t.getStatus() != LabTestStatus.REQUESTED) {
            throw ApiException.conflict("LAB_TEST_NOT_CANCELLABLE",
                    "Only a test that has not been started can be cancelled");
        }
        t.setStatus(LabTestStatus.CANCELLED);
        auditService.record(doctorUserId, AuditAction.LAB_TEST_CANCELLED, "LAB_TEST", t.getId(), null);
        return LabTestResponse.from(t);
    }

    // ------------------------------------------------------------------ patient / doctor: own tests

    /** Patients see tests ordered for them; doctors see tests they ordered. Nobody else's. */
    @Transactional(readOnly = true)
    public PageResponse<LabTestResponse> listMine(UserPrincipal caller, LabTestStatus status, int page, int size) {
        Pageable p = AdminService.pageable(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<LabTestRequest> result = switch (caller.getRole()) {
            case PATIENT -> status == null ? labRepo.findByPatientId(caller.getId(), p)
                    : labRepo.findByPatientIdAndStatus(caller.getId(), status, p);
            case DOCTOR -> status == null ? labRepo.findByOrderedById(caller.getId(), p)
                    : labRepo.findByOrderedByIdAndStatus(caller.getId(), status, p);
            default -> throw ApiException.forbidden("FORBIDDEN", "You do not have permission to perform this action");
        };
        return PageResponse.of(result.map(LabTestResponse::from));
    }

    // ------------------------------------------------------------------ lab technician

    @Transactional(readOnly = true)
    public PageResponse<LabTestResponse> queue(LabTestStatus status, int page, int size) {
        Pageable p = AdminService.pageable(page, size, Sort.by(Sort.Direction.ASC, "createdAt"));
        Page<LabTestRequest> result = status == null ? labRepo.findAllBy(p) : labRepo.findByStatus(status, p);
        return PageResponse.of(result.map(LabTestResponse::from));
    }

    @Transactional
    public LabTestResponse start(Long id, Long techId) {
        LabTestRequest t = labRepo.findByIdForUpdate(id)
                .orElseThrow(() -> ApiException.notFound("LAB_TEST_NOT_FOUND", "Lab test not found"));
        if (t.getStatus() != LabTestStatus.REQUESTED) {
            throw ApiException.conflict("LAB_TEST_NOT_STARTABLE", "This test is " + t.getStatus().name().toLowerCase()
                    + " and cannot be started");
        }
        Users tech = userRepo.findById(techId)
                .orElseThrow(() -> ApiException.unauthorized("UNAUTHENTICATED", "Authentication is required"));
        t.setProcessedBy(tech);
        t.setStatus(LabTestStatus.IN_PROGRESS);
        t.setStartedAt(Instant.now());
        auditService.record(techId, AuditAction.LAB_TEST_STARTED, "LAB_TEST", t.getId(), null);
        return LabTestResponse.from(t);
    }

    @Transactional
    public LabTestResponse complete(Long id, Long techId, String resultSummary) {
        LabTestRequest t = labRepo.findByIdForUpdate(id)
                .orElseThrow(() -> ApiException.notFound("LAB_TEST_NOT_FOUND", "Lab test not found"));
        if (t.getStatus() != LabTestStatus.IN_PROGRESS) {
            throw ApiException.conflict("LAB_TEST_NOT_COMPLETABLE",
                    "Only a test that is in progress can be completed");
        }
        if (t.getProcessedBy() == null || !t.getProcessedBy().getId().equals(techId)) {
            throw ApiException.forbidden("NOT_YOUR_TEST", "Only the technician who started this test can complete it");
        }
        t.setResultSummary(InputNormalizer.text(resultSummary));
        t.setStatus(LabTestStatus.COMPLETED);
        t.setCompletedAt(Instant.now());
        auditService.record(techId, AuditAction.LAB_TEST_COMPLETED, "LAB_TEST", t.getId(), null);
        return LabTestResponse.from(t);
    }
}
