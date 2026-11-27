package com.personal.medisynth.DTO;

import com.personal.medisynth.model.AppointmentStatus;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateAppointmentStatusRequest(@NotNull(message = "Status is required") AppointmentStatus status,
        @Size(max = 500) String reason) {
}
