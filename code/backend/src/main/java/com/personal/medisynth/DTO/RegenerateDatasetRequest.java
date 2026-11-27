package com.personal.medisynth.DTO;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** recordCount is optional: leave it out to keep the current size. */
public record RegenerateDatasetRequest(
        @Min(value = 500, message = "At least 500 records") @Max(value = 50000, message = "At most 50,000 records") Integer recordCount) {
}
