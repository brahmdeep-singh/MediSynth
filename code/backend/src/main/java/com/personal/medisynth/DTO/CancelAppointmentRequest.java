package com.personal.medisynth.DTO;

import jakarta.validation.constraints.Size;

public record CancelAppointmentRequest(@Size(max = 500) String reason) {
}
