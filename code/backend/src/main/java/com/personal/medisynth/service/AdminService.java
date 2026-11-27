package com.personal.medisynth.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.personal.medisynth.DTO.AppointmentResponse;
import com.personal.medisynth.DTO.DoctorProfileResponse;
import com.personal.medisynth.DTO.PageResponse;
import com.personal.medisynth.DTO.RegistrationRequestResponse;
import com.personal.medisynth.DTO.UserResponse;
import com.personal.medisynth.DTO.UserStatusUpdateRequest;
import com.personal.medisynth.exception.ApiException;
import com.personal.medisynth.model.AccountStatus;
import com.personal.medisynth.model.AppointmentStatus;
import com.personal.medisynth.model.AuditAction;
import com.personal.medisynth.model.DoctorProfile;
import com.personal.medisynth.model.RegistrationRequest;
import com.personal.medisynth.model.RequestStatus;
import com.personal.medisynth.model.Role;
import com.personal.medisynth.model.Users;
import com.personal.medisynth.model.VerificationStatus;
import com.personal.medisynth.repo.AppointmentRepo;
import com.personal.medisynth.repo.DoctorProfileRepo;
import com.personal.medisynth.repo.RegistrationRequestRepo;
import com.personal.medisynth.repo.UserRepo;

/** Read models and account-status management for the admin dashboard. */
@Service
public class AdminService {

    private static final int MAX_PAGE_SIZE = 100;

    private final UserRepo userRepo;
    private final DoctorProfileRepo doctorProfileRepo;
    private final RegistrationRequestRepo requestRepo;
    private final AppointmentRepo appointmentRepo;
    private final AuditService auditService;
    private final ApplicantDetailsService applicantDetails;

    public AdminService(UserRepo userRepo, DoctorProfileRepo doctorProfileRepo, RegistrationRequestRepo requestRepo,
            AppointmentRepo appointmentRepo, AuditService auditService, ApplicantDetailsService applicantDetails) {
        this.applicantDetails = applicantDetails;
        this.userRepo = userRepo;
        this.doctorProfileRepo = doctorProfileRepo;
        this.requestRepo = requestRepo;
        this.appointmentRepo = appointmentRepo;
        this.auditService = auditService;
    }

    static Pageable pageable(int page, int size, Sort sort) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE), sort);
    }

    // ------------------------------------------------------------------ users

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> listUsers(Role role, AccountStatus status, int page, int size) {
        Pageable p = pageable(page, size, Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        Page<Users> result;
        if (role != null && status != null) {
            result = userRepo.findByGrantedRoleAndAccountStatus(role, status, p);
        } else if (role != null) {
            result = userRepo.findByGrantedRole(role, p);
        } else if (status != null) {
            result = userRepo.findByAccountStatus(status, p);
        } else {
            result = userRepo.findAllBy(p);
        }
        return PageResponse.of(result.map(UserResponse::from));
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> listPatients(AccountStatus status, int page, int size) {
        return listUsers(Role.PATIENT, status, page, size);
    }

    @Transactional(readOnly = true)
    public PageResponse<DoctorProfileResponse> listDoctors(VerificationStatus verification, int page, int size) {
        Pageable p = pageable(page, size, Sort.by(Sort.Direction.DESC, "id"));
        Page<DoctorProfile> result = verification == null
                ? doctorProfileRepo.findAllBy(p)
                : doctorProfileRepo.findByVerificationStatus(verification, p);
        return PageResponse.of(result.map(DoctorProfileResponse::from));
    }

    // ------------------------------------------------------------------ appointments

    @Transactional(readOnly = true)
    public PageResponse<AppointmentResponse> listAppointments(AppointmentStatus status, int page, int size) {
        Pageable p = pageable(page, size, Sort.by(Sort.Direction.DESC, "startTime"));
        var result = status == null ? appointmentRepo.findAllBy(p) : appointmentRepo.findByStatus(status, p);
        return PageResponse.of(result.map(AppointmentResponse::from));
    }

    // ------------------------------------------------------------------ registration requests

    @Transactional(readOnly = true)
    public PageResponse<RegistrationRequestResponse> listRequests(RequestStatus status, Role requestedRole, int page,
            int size) {
        Pageable p = pageable(page, size, Sort.by(Sort.Direction.DESC, "submittedAt"));
        Page<RegistrationRequest> result;
        if (status != null && requestedRole != null) {
            result = requestRepo.findByStatusAndRequestedRole(status, requestedRole, p);
        } else if (status != null) {
            result = requestRepo.findByStatus(status, p);
        } else if (requestedRole != null) {
            result = requestRepo.findByRequestedRole(requestedRole, p);
        } else {
            result = requestRepo.findAllBy(p);
        }
        Set<Long> userIds = result.getContent().stream().map(r -> r.getUser().getId()).collect(Collectors.toSet());
        Map<Long, DoctorProfile> profiles = new HashMap<>();
        if (!userIds.isEmpty()) {
            for (DoctorProfile dp : doctorProfileRepo.findByUserIdIn(userIds)) {
                profiles.put(dp.getUser().getId(), dp);
            }
        }
        return PageResponse.of(result.map(r -> RegistrationRequestResponse.from(r,
                profiles.containsKey(r.getUser().getId())
                        ? DoctorProfileResponse.from(profiles.get(r.getUser().getId()))
                        : null,
                applicantDetails.resolve(r.getUser().getId(), r.getRequestedRole()))));
    }

    @Transactional(readOnly = true)
    public RegistrationRequestResponse getRequest(Long id) {
        RegistrationRequest r = requestRepo.findDetailedById(id)
                .orElseThrow(() -> ApiException.notFound("REQUEST_NOT_FOUND", "Registration request not found"));
        DoctorProfile profile = doctorProfileRepo.findByUserId(r.getUser().getId()).orElse(null);
        return RegistrationRequestResponse.from(r, profile == null ? null : DoctorProfileResponse.from(profile),
                applicantDetails.resolve(r.getUser().getId(), r.getRequestedRole()));
    }

    // ------------------------------------------------------------------ status management

    @Transactional
    public UserResponse updateUserStatus(Long adminId, Long targetId, UserStatusUpdateRequest request) {
        if (adminId.equals(targetId)) {
            throw ApiException.forbidden("SELF_STATUS_CHANGE_FORBIDDEN", "You cannot change your own account status");
        }
        AccountStatus requested = request.status();
        if (requested != AccountStatus.ACTIVE && requested != AccountStatus.SUSPENDED) {
            throw ApiException.badRequest("INVALID_STATUS",
                    "Status can only be set to ACTIVE or SUSPENDED. Use approve/reject for registration requests.");
        }
        Users target = userRepo.findById(targetId)
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "User not found"));
        if (target.getAccountStatus() == AccountStatus.PENDING_APPROVAL) {
            throw ApiException.conflict("USE_REGISTRATION_WORKFLOW",
                    "This account is awaiting approval. Approve or reject its registration request instead.");
        }
        AccountStatus before = target.getAccountStatus();
        if (before == requested) {
            return UserResponse.from(target);
        }
        target.setAccountStatus(requested);
        auditService.record(adminId, AuditAction.USER_STATUS_CHANGED, "USER", targetId,
                "from=" + before + ";to=" + requested);
        return UserResponse.from(target);
    }

    static List<AppointmentStatus> liveStatuses() {
        return AppointmentStatus.LIVE;
    }
}
