package com.hospital.triage.common;

/**
 * Cédula o TI colombiana: solo dígitos, de 5 a 10 (NUIP llega a 10).
 * No consulta a la Registraduría: evita cédulas de 20 números.
 */
public final class ColombianId {

    public static final String HINT = "La cédula o TI va de 5 a 10 dígitos, solo números. Ejemplo: 1 082 782 596.";

    private ColombianId() {
    }

    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException(HINT);
        }
        String digits = raw.replaceAll("\\D", "");
        if (digits.length() < 5 || digits.length() > 10) {
            throw new IllegalArgumentException(HINT);
        }
        return digits;
    }
}
