package com.hospital.triage.domain.enums;

/** Stored values stay stable for existing Postgres rows. */
public enum CareStatus {
    EN_ESPERA,
    EN_ATENCION,
    ATENDIDO,
    CANCELADO
}
