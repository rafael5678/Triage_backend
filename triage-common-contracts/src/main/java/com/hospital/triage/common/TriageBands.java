package com.hospital.triage.common;

import com.hospital.triage.common.enums.TriageLevel;

/**
 * Shared ESI score bands. Protocol, catalog floor and Gemini merge all read from here
 * so the numbers are not scattered as magic literals.
 */
public final class TriageBands {

    public static final int ESI_I = 78;
    public static final int ESI_II = 62;
    public static final int ESI_III = 42;
    public static final int ESI_IV = 22;
    public static final int RESUSCITATION = 86;
    public static final int CARDIAC_ARREST = 97;
    public static final int MAX = 100;
    public static final int MIN = 0;

    private TriageBands() {}

    public static int esiFloor(int conditionSeverity) {
        if (conditionSeverity >= ESI_I) {
            return ESI_I;
        }
        if (conditionSeverity >= ESI_II) {
            return ESI_II;
        }
        if (conditionSeverity >= ESI_III) {
            return ESI_III;
        }
        return MIN;
    }

    public static TriageLevel fromScore(double score) {
        if (score >= ESI_I) {
            return TriageLevel.TRIAGE_I_ROJO;
        }
        if (score >= ESI_II) {
            return TriageLevel.TRIAGE_II_NARANJA;
        }
        if (score >= ESI_III) {
            return TriageLevel.TRIAGE_III_AMARILLO;
        }
        if (score >= ESI_IV) {
            return TriageLevel.TRIAGE_IV_VERDE;
        }
        return TriageLevel.TRIAGE_V_AZUL;
    }
}
