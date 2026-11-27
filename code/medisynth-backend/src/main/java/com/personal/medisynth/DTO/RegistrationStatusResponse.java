package com.personal.medisynth.DTO;

import java.time.Instant;

import com.personal.medisynth.model.RegistrationRequest;
import com.personal.medisynth.model.RequestStatus;
import com.personal.medisynth.model.Role;

/** Deliberately minimal public view of one request, looked up by its secret reference. */
public record RegistrationStatusResponse(
        RequestStatus status,
        Role requestedRole,
        Instant submittedAt,
        Instant reviewedAt,
        String message,
        String rejectionReason) {

    public static RegistrationStatusResponse from(RegistrationRequest r) {
        String message = switch (r.getStatus()) {
            case PENDING_APPROVAL -> "Your request is awaiting admin review. You cannot log in yet.";
            case APPROVED -> "Your request was approved. You can now log in.";
            case REJECTED -> "Your request was not approved.";
        };
        // The reason is only shown for rejected requests and is written for the applicant by the admin.
        String reason = r.getStatus() == RequestStatus.REJECTED ? r.getRejectionReason() : null;
        return new RegistrationStatusResponse(r.getStatus(), r.getRequestedRole(), r.getSubmittedAt(),
                r.getReviewedAt(), message, reason);
    }
}
