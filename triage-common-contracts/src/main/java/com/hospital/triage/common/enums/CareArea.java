package com.hospital.triage.common.enums;

/** Área de urgencias a la que se envía al paciente, de la más a la menos urgente. */
public enum CareArea {
    REANIMACION("Reanimación (sala de choque)", 0),
    URGENCIAS_CRITICAS("Urgencias críticas y observación", 10),
    URGENCIAS("Urgencias generales", 30),
    CONSULTA_PRIORITARIA("Consulta prioritaria", 60),
    CONSULTA_EXTERNA("Consulta externa u orientación", 120);

    private final String label;
    private final int maxWaitMinutes;

    CareArea(String label, int maxWaitMinutes) {
        this.label = label;
        this.maxWaitMinutes = maxWaitMinutes;
    }

    public String label() {
        return label;
    }

    public int maxWaitMinutes() {
        return maxWaitMinutes;
    }

    /** Más urgente que otra área (REANIMACION es la más urgente). */
    public boolean moreUrgentThan(CareArea other) {
        return other == null || ordinal() < other.ordinal();
    }

    public static CareArea fromLevel(TriageLevel level) {
        if (level == null) {
            return URGENCIAS;
        }
        return switch (level) {
            case TRIAGE_I_ROJO -> REANIMACION;
            case TRIAGE_II_NARANJA -> URGENCIAS_CRITICAS;
            case TRIAGE_III_AMARILLO -> URGENCIAS;
            case TRIAGE_IV_VERDE -> CONSULTA_PRIORITARIA;
            case TRIAGE_V_AZUL -> CONSULTA_EXTERNA;
        };
    }

    public static CareArea parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
