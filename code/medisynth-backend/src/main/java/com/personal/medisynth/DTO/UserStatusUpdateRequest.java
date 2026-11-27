package com.personal.medisynth.DTO;

import com.personal.medisynth.model.AccountStatus;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UserStatusUpdateRequest(@NotNull(message = "Status is required") AccountStatus status,
        @Size(max = 500) String reason) {
}
