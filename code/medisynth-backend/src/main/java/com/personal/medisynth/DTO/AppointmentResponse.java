package com.personal.medisynth.DTO;

import java.time.Instant;

import com.personal.medisynth.model.Appointment;
import com.personal.medisynth.model.AppointmentStatus;

public record AppointmentResponse(Long id, Long patientId, String patientName, Long doctorId, String doctorName,
        String specialization, Instant appointmentTime, String reason, AppointmentStatus status,
        String cancellationReason, Instant createdAt, Instant updatedAt) {

    public static AppointmentResponse from(Appointment a) {
        return new AppointmentResponse(a.getId(), a.getPatient().getId(), a.getPatient().getName(),
                a.getDoctor().getId(), a.getDoctor().getUser().getName(), a.getDoctor().getSpecialization(),
                a.getStartTime(), a.getReason(), a.getStatus(), a.getCancellationReason(), a.getCreatedAt(),
                a.getUpdatedAt());
    }
}
