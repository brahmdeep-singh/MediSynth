package com.personal.medisynth.DTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** The patient is taken from the appointment, never from the body. */
public record OrderLabTestRequest(
        @NotNull(message = "appointmentId is required") Long appointmentId,
        @NotBlank(message = "Test type is required") @Size(max = 100) String testType,
        @Size(max = 500) String notes) {
}
