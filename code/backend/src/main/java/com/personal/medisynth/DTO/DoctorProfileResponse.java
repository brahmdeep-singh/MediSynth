package com.personal.medisynth.DTO;

import java.math.BigDecimal;
import java.time.Instant;

import com.personal.medisynth.model.AccountStatus;
import com.personal.medisynth.model.DoctorProfile;
import com.personal.medisynth.model.VerificationStatus;

/** Private doctor view (the doctor themself, or an admin). Includes the license number. */
public record DoctorProfileResponse(Long id, Long userId, String name, String email, String phone,
        AccountStatus accountStatus, String specialization, String qualifications, Integer experienceYears,
        String licenseNumber, BigDecimal consultationFee, String availability, VerificationStatus verificationStatus,
        Instant verifiedAt) {

    public static DoctorProfileResponse from(DoctorProfile p) {
        return new DoctorProfileResponse(p.getId(), p.getUser().getId(), p.getUser().getName(), p.getUser().getEmail(),
                p.getUser().getPhone(), p.getUser().getAccountStatus(), p.getSpecialization(), p.getQualifications(),
                p.getExperienceYears(), p.getLicenseNumber(), p.getConsultationFee(), p.getAvailability(),
                p.getVerificationStatus(), p.getVerifiedAt());
    }
}
