package com.hospital.triage.domain.enums;

/** Stored values stay stable for existing Postgres rows. */
public enum TriageLevel {
    TRIAGE_I_ROJO,
    TRIAGE_II_NARANJA,
    TRIAGE_III_AMARILLO,
    TRIAGE_IV_VERDE,
    TRIAGE_V_AZUL
}
