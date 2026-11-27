package com.personal.medisynth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.personal.medisynth.DTO.DoctorProfileResponse;
import com.personal.medisynth.DTO.RegisterRequest;
import com.personal.medisynth.DTO.RegisterResponse;
import com.personal.medisynth.DTO.RegistrationRequestResponse;
import com.personal.medisynth.DTO.RegistrationStatusResponse;
import com.personal.medisynth.exception.ApiException;
import com.personal.medisynth.model.AccountStatus;
import com.personal.medisynth.model.AuditAction;
import com.personal.medisynth.model.DoctorProfile;
import com.personal.medisynth.model.LabTechnicianProfile;
import com.personal.medisynth.model.ResearcherProfile;
import com.personal.medisynth.model.RegistrationRequest;
import com.personal.medisynth.model.RequestStatus;
import com.personal.medisynth.model.Role;
import com.personal.medisynth.model.Users;
import com.personal.medisynth.model.VerificationStatus;
import com.personal.medisynth.repo.DoctorProfileRepo;
import com.personal.medisynth.repo.LabTechnicianProfileRepo;
import com.personal.medisynth.repo.ResearcherProfileRepo;
import com.personal.medisynth.repo.RegistrationRequestRepo;
import com.personal.medisynth.repo.UserRepo;

/**
 * Non-patient registration lifecycle:
 * submitted (persisted PENDING_APPROVAL) -> admin approves or rejects (updates the SAME rows, in one transaction).
 */
@Service
public class RegistrationService {

    /**
     * Approval policy: the only roles an admin may grant through a registration request.
     * ADMIN is intentionally absent; adding a future role here is a deliberate, reviewed change.
     * Any role not listed here can never be granted by approval.
     */
    static final Set<Role> APPROVABLE_ROLES = Set.of(Role.DOCTOR, Role.LAB_TECHNICIAN, Role.RESEARCHER);

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepo userRepo;
    private final DoctorProfileRepo doctorProfileRepo;
    private final RegistrationRequestRepo requestRepo;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final LabTechnicianProfileRepo labProfileRepo;
    private final ResearcherProfileRepo researcherProfileRepo;
    private final ApplicantDetailsService applicantDetails;

    public RegistrationService(UserRepo userRepo, DoctorProfileRepo doctorProfileRepo,
            RegistrationRequestRepo requestRepo, PasswordEncoder passwordEncoder, AuditService auditService,
            LabTechnicianProfileRepo labProfileRepo, ResearcherProfileRepo researcherProfileRepo,
            ApplicantDetailsService applicantDetails) {
        this.labProfileRepo = labProfileRepo;
        this.researcherProfileRepo = researcherProfileRepo;
        this.applicantDetails = applicantDetails;
        this.userRepo = userRepo;
        this.doctorProfileRepo = doctorProfileRepo;
        this.requestRepo = requestRepo;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    // ------------------------------------------------------------------ submission

    @Transactional
    public RegisterResponse submitDoctorApplication(RegisterRequest req) {
        Map<String, String> missing = new LinkedHashMap<>();
        if (InputNormalizer.blankToNull(req.specialization()) == null) {
            missing.put("specialization", "Specialization is required for doctor registration");
        }
        if (InputNormalizer.blankToNull(req.qualifications()) == null) {
            missing.put("qualifications", "Qualifications are required for doctor registration");
        }
        if (req.experienceYears() == null) {
            missing.put("experienceYears", "Experience is required for doctor registration");
        }
        if (InputNormalizer.blankToNull(req.licenseNumber()) == null) {
            missing.put("licenseNumber", "License number is required for doctor registration");
        }
        if (!missing.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "One or more fields are invalid", missing);
        }

        String email = InputNormalizer.email(req.email());
        Users existing = userRepo.findByEmail(email).orElse(null);
        if (existing != null) {
            if (requestRepo.existsByUserIdAndStatus(existing.getId(), RequestStatus.PENDING_APPROVAL)) {
                throw ApiException.conflict("REGISTRATION_ALREADY_PENDING",
                        "A registration request for this email is already awaiting approval");
            }
            throw ApiException.conflict("EMAIL_ALREADY_REGISTERED", "This email is already registered");
        }
        String license = InputNormalizer.license(req.licenseNumber());
        if (doctorProfileRepo.existsByLicenseNumber(license)) {
            throw ApiException.conflict("LICENSE_ALREADY_REGISTERED", "This license number is already registered");
        }

        // 1. Account exists but is inert: PENDING_APPROVAL accounts cannot log in or hold any privileged role.
        Users user = new Users();
        user.setName(InputNormalizer.text(req.name()));
        user.setEmail(email);
        user.setPhone(InputNormalizer.text(req.phone()));
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setGrantedRole(Role.PATIENT); // least-privilege placeholder; DOCTOR is only granted on approval
        user.setAccountStatus(AccountStatus.PENDING_APPROVAL);
        userRepo.saveAndFlush(user);

        // 2. Professional details are stored, but stay unverified until an admin approves.
        DoctorProfile profile = new DoctorProfile();
        profile.setUser(user);
        profile.setSpecialization(InputNormalizer.text(req.specialization()));
        profile.setQualifications(InputNormalizer.text(req.qualifications()));
        profile.setExperienceYears(req.experienceYears());
        profile.setLicenseNumber(license);
        profile.setConsultationFee(req.consultationFee() == null ? java.math.BigDecimal.ZERO : req.consultationFee());
        profile.setAvailability(InputNormalizer.blankToNull(req.availability()));
        profile.setVerificationStatus(VerificationStatus.PENDING);
        doctorProfileRepo.save(profile);

        // 3. The request itself is persisted NOW, before any approval.
        String reference = newReference();
        RegistrationRequest request = new RegistrationRequest();
        request.setUser(user);
        request.setRequestedRole(Role.DOCTOR);
        request.setStatus(RequestStatus.PENDING_APPROVAL);
        request.setStatusTokenHash(hash(reference));
        request.setSubmittedAt(Instant.now());
        requestRepo.saveAndFlush(request);

        auditService.record(user.getId(), AuditAction.DOCTOR_REGISTRATION_SUBMITTED, "REGISTRATION_REQUEST",
                request.getId(), "requestedRole=DOCTOR");
        return RegisterResponse.pending(reference);
    }

    /**
     * Lab technician / researcher application. Same safety model as doctors: the account is created inert
     * (PENDING_APPROVAL, cannot log in), the request is persisted immediately, and the role is only granted
     * when an admin approves.
     */
    @Transactional
    public RegisterResponse submitStaffApplication(RegisterRequest req, Role role) {
        if (role != Role.LAB_TECHNICIAN && role != Role.RESEARCHER) {
            throw ApiException.badRequest("INVALID_ROLE", "Unsupported role for a staff application");
        }
        Map<String, String> missing = new LinkedHashMap<>();
        if (role == Role.LAB_TECHNICIAN) {
            if (InputNormalizer.blankToNull(req.department()) == null) {
                missing.put("department", "Department is required for lab technician registration");
            }
            if (InputNormalizer.blankToNull(req.employeeId()) == null) {
                missing.put("employeeId", "Employee ID is required for lab technician registration");
            }
        } else {
            if (InputNormalizer.blankToNull(req.institution()) == null) {
                missing.put("institution", "Institution is required for researcher registration");
            }
            if (InputNormalizer.blankToNull(req.researchArea()) == null) {
                missing.put("researchArea", "Research area is required for researcher registration");
            }
        }
        if (!missing.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "One or more fields are invalid", missing);
        }

        String email = InputNormalizer.email(req.email());
        Users existing = userRepo.findByEmail(email).orElse(null);
        if (existing != null) {
            if (requestRepo.existsByUserIdAndStatus(existing.getId(), RequestStatus.PENDING_APPROVAL)) {
                throw ApiException.conflict("REGISTRATION_ALREADY_PENDING",
                        "A registration request for this email is already awaiting approval");
            }
            throw ApiException.conflict("EMAIL_ALREADY_REGISTERED", "This email is already registered");
        }
        String employeeId = role == Role.LAB_TECHNICIAN ? InputNormalizer.license(req.employeeId()) : null;
        if (employeeId != null && labProfileRepo.existsByEmployeeId(employeeId)) {
            throw ApiException.conflict("EMPLOYEE_ID_ALREADY_REGISTERED", "This employee ID is already registered");
        }

        Users user = new Users();
        user.setName(InputNormalizer.text(req.name()));
        user.setEmail(email);
        user.setPhone(InputNormalizer.text(req.phone()));
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setGrantedRole(Role.PATIENT); // least-privilege placeholder; the requested role is only granted on approval
        user.setAccountStatus(AccountStatus.PENDING_APPROVAL);
        userRepo.saveAndFlush(user);

        if (role == Role.LAB_TECHNICIAN) {
            LabTechnicianProfile profile = new LabTechnicianProfile();
            profile.setUser(user);
            profile.setDepartment(InputNormalizer.text(req.department()));
            profile.setEmployeeId(employeeId);
            labProfileRepo.save(profile);
        } else {
            ResearcherProfile profile = new ResearcherProfile();
            profile.setUser(user);
            profile.setInstitution(InputNormalizer.text(req.institution()));
            profile.setResearchArea(InputNormalizer.text(req.researchArea()));
            researcherProfileRepo.save(profile);
        }

        String reference = newReference();
        RegistrationRequest request = new RegistrationRequest();
        request.setUser(user);
        request.setRequestedRole(role);
        request.setStatus(RequestStatus.PENDING_APPROVAL);
        request.setStatusTokenHash(hash(reference));
        request.setSubmittedAt(Instant.now());
        requestRepo.saveAndFlush(request);

        auditService.record(user.getId(), AuditAction.STAFF_REGISTRATION_SUBMITTED, "REGISTRATION_REQUEST",
                request.getId(), "requestedRole=" + role);
        return RegisterResponse.pending(reference);
    }

    // ------------------------------------------------------------------ public status check

    /**
     * Looks up exactly one request by its secret reference (256-bit random, only the hash is stored).
     * Unknown/malformed references all produce the same 404, so nothing can be enumerated.
     */
    @Transactional(readOnly = true)
    public RegistrationStatusResponse getStatus(String reference) {
        if (reference == null || reference.isBlank() || reference.length() > 128) {
            throw notFoundReference();
        }
        return requestRepo.findByStatusTokenHash(hash(reference.trim()))
                .map(RegistrationStatusResponse::from)
                .orElseThrow(RegistrationService::notFoundReference);
    }

    private static ApiException notFoundReference() {
        return ApiException.notFound("REGISTRATION_NOT_FOUND", "No registration request matches that reference");
    }

    // ------------------------------------------------------------------ admin decisions

    @Transactional
    public RegistrationRequestResponse approve(Long requestId, Long adminId) {
        Users admin = requireActiveAdmin(adminId);
        RegistrationRequest request = requestRepo.findByIdForUpdate(requestId)
                .orElseThrow(() -> ApiException.notFound("REQUEST_NOT_FOUND", "Registration request not found"));
        Users applicant = guardDecision(request, admin);

        if (!APPROVABLE_ROLES.contains(request.getRequestedRole())) {
            throw ApiException.forbidden("ROLE_NOT_APPROVABLE", "This role cannot be granted through approval");
        }

        Instant now = Instant.now();
        request.setStatus(RequestStatus.APPROVED);
        request.setReviewedAt(now);
        request.setReviewedBy(admin);

        applicant.setGrantedRole(request.getRequestedRole()); // the ONLY place a requested role becomes a granted role
        applicant.setAccountStatus(AccountStatus.ACTIVE);

        DoctorProfile profile = null;
        if (request.getRequestedRole() == Role.DOCTOR) {
            profile = doctorProfileRepo.findByUserId(applicant.getId())
                    .orElseThrow(() -> ApiException.conflict("DOCTOR_PROFILE_MISSING",
                            "The doctor profile for this request is missing; nothing was changed"));
            profile.setVerificationStatus(VerificationStatus.VERIFIED);
            profile.setVerifiedAt(now);
        }

        auditService.record(adminId, AuditAction.REGISTRATION_APPROVED, "REGISTRATION_REQUEST", request.getId(),
                "applicantId=" + applicant.getId() + ";grantedRole=" + request.getRequestedRole());
        return RegistrationRequestResponse.from(request, profile == null ? null : DoctorProfileResponse.from(profile),
                applicantDetails.resolve(applicant.getId(), request.getRequestedRole()));
    }

    @Transactional
    public RegistrationRequestResponse reject(Long requestId, Long adminId, String reason) {
        Users admin = requireActiveAdmin(adminId);
        RegistrationRequest request = requestRepo.findByIdForUpdate(requestId)
                .orElseThrow(() -> ApiException.notFound("REQUEST_NOT_FOUND", "Registration request not found"));
        Users applicant = guardDecision(request, admin);

        Instant now = Instant.now();
        request.setStatus(RequestStatus.REJECTED);
        request.setReviewedAt(now);
        request.setReviewedBy(admin);
        request.setRejectionReason(InputNormalizer.blankToNull(reason));

        // Nothing is granted: the account stays a PATIENT-baseline row but can no longer log in (REJECTED).
        applicant.setAccountStatus(AccountStatus.REJECTED);

        DoctorProfile profile = doctorProfileRepo.findByUserId(applicant.getId()).orElse(null);
        if (profile != null) {
            profile.setVerificationStatus(VerificationStatus.REJECTED);
        }

        auditService.record(adminId, AuditAction.REGISTRATION_REJECTED, "REGISTRATION_REQUEST", request.getId(),
                "applicantId=" + applicant.getId() + ";reasonProvided=" + (request.getRejectionReason() != null));
        return RegistrationRequestResponse.from(request, profile == null ? null : DoctorProfileResponse.from(profile),
                applicantDetails.resolve(applicant.getId(), request.getRequestedRole()));
    }

    // ------------------------------------------------------------------ helpers

    private Users requireActiveAdmin(Long adminId) {
        Users admin = userRepo.findById(adminId)
                .orElseThrow(() -> ApiException.unauthorized("UNAUTHENTICATED", "Authentication is required"));
        if (admin.getGrantedRole() != Role.ADMIN || admin.getAccountStatus() != AccountStatus.ACTIVE) {
            throw ApiException.forbidden("FORBIDDEN", "Only active administrators can decide registration requests");
        }
        return admin;
    }

    /** Shared pre-conditions for approve/reject. Returns the applicant. */
    private Users guardDecision(RegistrationRequest request, Users admin) {
        Users applicant = request.getUser();
        if (applicant.getId().equals(admin.getId())) {
            throw ApiException.forbidden("SELF_APPROVAL_FORBIDDEN", "You cannot decide your own registration request");
        }
        if (request.getStatus() != RequestStatus.PENDING_APPROVAL) {
            throw ApiException.conflict("REQUEST_ALREADY_DECIDED",
                    "This request has already been " + request.getStatus().name().toLowerCase());
        }
        if (applicant.getAccountStatus() != AccountStatus.PENDING_APPROVAL) {
            throw ApiException.conflict("APPLICANT_STATE_INVALID",
                    "The applicant account is not awaiting approval");
        }
        return applicant;
    }

    private static String newReference() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String reference) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(reference.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
