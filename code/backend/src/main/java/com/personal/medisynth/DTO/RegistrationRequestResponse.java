package com.personal.medisynth.DTO;

import java.time.Instant;

import com.personal.medisynth.model.AccountStatus;
import com.personal.medisynth.model.RegistrationRequest;
import com.personal.medisynth.model.RequestStatus;
import com.personal.medisynth.model.Role;

/** Admin view of a registration request, including the applicant and (for doctors) the submitted profile. */
public record RegistrationRequestResponse(
        Long id,
        Long applicantId,
        String applicantName,
        String applicantEmail,
        String applicantPhone,
        AccountStatus applicantAccountStatus,
        Role requestedRole,
        RequestStatus status,
        Instant submittedAt,
        Instant reviewedAt,
        Long reviewedById,
        String reviewedByName,
        String rejectionReason,
        DoctorProfileResponse doctorProfile,
        /** Lab technician / researcher application details (null for doctors and patients). */
        java.util.Map<String, String> applicantDetails) {

    public static RegistrationRequestResponse from(RegistrationRequest r, DoctorProfileResponse doctorProfile) {
        return from(r, doctorProfile, null);
    }

    public static RegistrationRequestResponse from(RegistrationRequest r, DoctorProfileResponse doctorProfile,
            java.util.Map<String, String> applicantDetails) {
        return new RegistrationRequestResponse(r.getId(), r.getUser().getId(), r.getUser().getName(),
                r.getUser().getEmail(), r.getUser().getPhone(), r.getUser().getAccountStatus(), r.getRequestedRole(),
                r.getStatus(), r.getSubmittedAt(), r.getReviewedAt(),
                r.getReviewedBy() == null ? null : r.getReviewedBy().getId(),
                r.getReviewedBy() == null ? null : r.getReviewedBy().getName(), r.getRejectionReason(),
                doctorProfile, applicantDetails);
    }
}
