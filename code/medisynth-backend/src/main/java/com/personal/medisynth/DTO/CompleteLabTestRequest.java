package com.personal.medisynth.DTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CompleteLabTestRequest(
        @NotBlank(message = "Result summary is required") @Size(max = 1000) String resultSummary) {
}
