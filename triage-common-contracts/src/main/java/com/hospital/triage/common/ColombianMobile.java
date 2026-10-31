package com.hospital.triage.common;

/**
 * Celular colombiano para WhatsApp: 10 dígitos que empiezan en 3 (300, 310, 318…).
 * Vacío se permite (el aviso simplemente no se manda).
 */
public final class ColombianMobile {

    public static final String HINT = "Use un celular colombiano de 10 dígitos que empiece por 3 (ejemplo 318 668 0042).";

    private ColombianMobile() {
    }

    /** null si viene vacío; si hay texto, tiene que ser válido o lanza. */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String digits = raw.replaceAll("\\D", "");
        if (digits.startsWith("57") && digits.length() >= 12) {
            digits = digits.substring(2);
        }
        if (digits.length() > 10) {
            digits = digits.substring(0, 10);
        }
        if (!validLocal(digits)) {
            throw new IllegalArgumentException(HINT);
        }
        return "57" + digits;
    }

    public static boolean validLocal(String tenDigits) {
        if (tenDigits == null || tenDigits.length() != 10) {
            return false;
        }
        if (!tenDigits.matches("3[0-5]\\d{8}")) {
            return false;
        }
        return tenDigits.chars().distinct().count() > 1;
    }
}
