package com.personal.medisynth.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.personal.medisynth.DTO.DoctorProfileResponse;
import com.personal.medisynth.DTO.DoctorPublicResponse;
import com.personal.medisynth.DTO.PageResponse;
import com.personal.medisynth.DTO.UpdateDoctorProfileRequest;
import com.personal.medisynth.exception.ApiException;
import com.personal.medisynth.model.AccountStatus;
import com.personal.medisynth.model.DoctorProfile;
import com.personal.medisynth.model.Role;
import com.personal.medisynth.model.UserPrincipal;
import com.personal.medisynth.model.Users;
import com.personal.medisynth.model.VerificationStatus;
import com.personal.medisynth.repo.DoctorProfileRepo;

@Service
public class DoctorService {

    private final DoctorProfileRepo doctorProfileRepo;

    public DoctorService(DoctorProfileRepo doctorProfileRepo) {
        this.doctorProfileRepo = doctorProfileRepo;
    }

    /** Only VERIFIED doctors with an ACTIVE account are listed. Pending/rejected applicants are invisible. */
    @Transactional(readOnly = true)
    public PageResponse<DoctorPublicResponse> listBookable(String specialization, int page, int size) {
        Pageable p = AdminService.pageable(page, size, Sort.by(Sort.Direction.ASC, "id"));
        String spec = InputNormalizer.blankToNull(specialization);
        Page<DoctorProfile> result = spec == null
                ? doctorProfileRepo.findByVerificationStatusAndUserAccountStatus(VerificationStatus.VERIFIED,
                        AccountStatus.ACTIVE, p)
                : doctorProfileRepo.findByVerificationStatusAndUserAccountStatusAndSpecializationContainingIgnoreCase(
                        VerificationStatus.VERIFIED, AccountStatus.ACTIVE, spec, p);
        return PageResponse.of(result.map(DoctorPublicResponse::from));
    }

    /** Non-bookable doctors look like "not found" to everyone except admins and the doctor themself. */
    @Transactional(readOnly = true)
    public DoctorPublicResponse getPublic(Long doctorId, UserPrincipal caller) {
        DoctorProfile profile = doctorProfileRepo.findDetailedById(doctorId)
                .orElseThrow(DoctorService::notFound);
        boolean visible = profile.getVerificationStatus() == VerificationStatus.VERIFIED
                && profile.getUser().getAccountStatus() == AccountStatus.ACTIVE;
        boolean owner = profile.getUser().getId().equals(caller.getId());
        if (!visible && !owner && caller.getRole() != Role.ADMIN) {
            throw notFound();
        }
        return DoctorPublicResponse.from(profile);
    }

    @Transactional(readOnly = true)
    public DoctorProfileResponse getMe(Long userId) {
        return DoctorProfileResponse.from(requireProfile(userId));
    }

    /** Whitelisted fields only: license number and verification status can never be edited here. */
    @Transactional
    public DoctorProfileResponse updateMe(Long userId, UpdateDoctorProfileRequest req) {
        DoctorProfile profile = requireProfile(userId);
        Users user = profile.getUser();
        user.setName(InputNormalizer.text(req.name()));
        user.setPhone(InputNormalizer.text(req.phone()));
        profile.setSpecialization(InputNormalizer.text(req.specialization()));
        profile.setQualifications(InputNormalizer.text(req.qualifications()));
        profile.setExperienceYears(req.experienceYears());
        profile.setConsultationFee(req.consultationFee());
        profile.setAvailability(InputNormalizer.blankToNull(req.availability()));
        return DoctorProfileResponse.from(profile);
    }

    private DoctorProfile requireProfile(Long userId) {
        return doctorProfileRepo.findByUserId(userId)
                .orElseThrow(() -> ApiException.notFound("DOCTOR_PROFILE_NOT_FOUND", "Doctor profile not found"));
    }

    private static ApiException notFound() {
        return ApiException.notFound("DOCTOR_NOT_FOUND", "Doctor not found");
    }
}
