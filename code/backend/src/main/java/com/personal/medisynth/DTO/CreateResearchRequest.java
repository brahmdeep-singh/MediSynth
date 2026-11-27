package com.personal.medisynth.DTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Name a catalogue dataset (datasetId) or, for data that is not in the catalogue, describe it in datasetTitle.
 * At least one of the two is required; the service enforces that.
 */
public record CreateResearchRequest(
        Long datasetId,
        @Size(max = 150) String datasetTitle,
        @NotBlank(message = "Purpose is required") @Size(max = 1000) String purpose,
        @Size(max = 500) String dataScope) {
}
