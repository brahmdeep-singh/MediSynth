package com.personal.medisynth.DTO;

import java.time.Instant;

import com.personal.medisynth.model.LabTestRequest;
import com.personal.medisynth.model.LabTestStatus;

public record LabTestResponse(Long id, Long appointmentId, Long patientId, String patientName, Long orderedById,
        String orderedByName, String testType, String clinicalNotes, LabTestStatus status, String resultSummary,
        Long processedById, String processedByName, Instant startedAt, Instant completedAt, Instant createdAt) {

    public static LabTestResponse from(LabTestRequest t) {
        return new LabTestResponse(t.getId(),
                t.getAppointment() == null ? null : t.getAppointment().getId(),
                t.getPatient().getId(), t.getPatient().getName(),
                t.getOrderedBy().getId(), t.getOrderedBy().getName(),
                t.getTestType(), t.getClinicalNotes(), t.getStatus(), t.getResultSummary(),
                t.getProcessedBy() == null ? null : t.getProcessedBy().getId(),
                t.getProcessedBy() == null ? null : t.getProcessedBy().getName(),
                t.getStartedAt(), t.getCompletedAt(), t.getCreatedAt());
    }
}
