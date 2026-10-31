package com.hospital.triage.common;

import java.util.Set;

/**
 * Celular colombiano para WhatsApp: 10 dígitos que empiezan en 3, con prefijo real,
 * no una secuencia inventada. Vacío se permite (el aviso simplemente no se manda).
 */
public final class ColombianMobile {

    private static final Set<String> PREFIXES = Set.of(
            "300", "301", "302", "303", "304", "305",
            "310", "311", "312", "313", "314", "315", "316", "317", "318", "319",
            "320", "321", "322", "323", "324", "333",
            "350", "351", "352", "353"
    );

    public static final String HINT = "Use un celular colombiano de 10 dígitos que empiece por 3 (ejemplo 300 123 4567). No se aceptan números inventados.";

    private ColombianMobile() {
    }

    /** null si viene vacío; si hay texto, tiene que ser válido o lanza. */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String digits = raw.replaceAll("\\D", "");
        if (digits.startsWith("57") && digits.length() == 12) {
            digits = digits.substring(2);
        }
        if (!validLocal(digits)) {
            throw new IllegalArgumentException(HINT);
        }
        return "57" + digits;
    }

    public static boolean validLocal(String tenDigits) {
        if (tenDigits == null || tenDigits.length() != 10 || !tenDigits.startsWith("3")) {
            return false;
        }
        if (!PREFIXES.contains(tenDigits.substring(0, 3))) {
            return false;
        }
        if (tenDigits.chars().distinct().count() <= 2) {
            return false;
        }
        if (tenDigits.matches("3\\d0{8}")) {
            return false;
        }
        String rest = tenDigits.substring(3);
        return rest.chars().distinct().count() > 1;
    }
}
