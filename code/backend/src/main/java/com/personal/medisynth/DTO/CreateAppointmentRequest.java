package com.personal.medisynth.DTO;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** The patient is always the authenticated caller; it is never read from the body. */
public record CreateAppointmentRequest(
        @NotNull(message = "doctorId is required") Long doctorId,
        @NotNull(message = "appointmentTime is required (ISO-8601, e.g. 2026-12-01T10:00:00Z)") Instant appointmentTime,
        @NotBlank(message = "Reason is required") @Size(max = 500) String reason) {
}
