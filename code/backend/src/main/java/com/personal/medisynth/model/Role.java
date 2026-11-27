package com.personal.medisynth.model;

/** Authoritative role granted by the server. Never taken from a client payload or token claim. */
public enum Role {
    PATIENT,
    DOCTOR,
    ADMIN,
    LAB_TECHNICIAN,
    RESEARCHER
}
