package com.personal.medisynth.DTO;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateDatasetRequest(
        @NotBlank(message = "Dataset name is required") @Size(max = 150) String name,
        @Size(max = 500) String description,
        @Min(value = 500, message = "At least 500 records") @Max(value = 50000, message = "At most 50,000 records") int recordCount,
        Long seed) {
}
