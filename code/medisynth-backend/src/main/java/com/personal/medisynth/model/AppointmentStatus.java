package com.personal.medisynth.model;

import java.util.List;

public enum AppointmentStatus {
    PENDING,
    CONFIRMED,
    CANCELLED,
    COMPLETED;

    /** Statuses that occupy a slot (must match the partial unique indexes in the migration). */
    public static final List<AppointmentStatus> LIVE = List.of(PENDING, CONFIRMED);
}
