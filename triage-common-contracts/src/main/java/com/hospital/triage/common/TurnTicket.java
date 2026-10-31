package com.hospital.triage.common;

/**
 * Turno de sala: iniciales (máximo 2) y los últimos 3 del documento.
 * No identifica a la persona en la pantalla pública.
 */
public final class TurnTicket {

    public static final int ELDERLY_FROM_YEARS = 65;

    private TurnTicket() {
    }

    public static String of(String name, String document) {
        StringBuilder initials = new StringBuilder();
        if (name != null) {
            for (String part : name.trim().split("\\s+")) {
                if (!part.isBlank() && initials.length() < 2) {
                    initials.append(Character.toUpperCase(part.charAt(0)));
                }
            }
        }
        if (initials.isEmpty()) {
            initials.append("P");
        }
        String doc = document == null ? "" : document.replaceAll("\\s", "");
        String tail = doc.length() >= 3 ? doc.substring(doc.length() - 3) : doc;
        return tail.isEmpty() ? initials.toString() : initials + "-" + tail;
    }

    public static boolean elderly(Integer age) {
        return age != null && age >= ELDERLY_FROM_YEARS;
    }
}
