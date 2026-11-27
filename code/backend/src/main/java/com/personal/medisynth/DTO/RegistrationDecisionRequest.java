package com.personal.medisynth.DTO;

import jakarta.validation.constraints.Size;

/** Optional body for reject. The reason is shown to the applicant through the status check. */
public record RegistrationDecisionRequest(@Size(max = 500) String reason) {
}
