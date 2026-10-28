package com.hospital.triage.evaluation.application.service;

/**
 * Rangos normales de signos vitales por edad (referencia pediátrica tipo PEWS/PALS).
 * Cada signo suma 0 si está en rango y hasta 3 según cuánto se aleja, en la misma escala que NEWS2.
 */
record PediatricBand(int hrMin, int hrMax, int rrMin, int rrMax, int sbpMin) {

    static PediatricBand forAge(int age) {
        if (age >= 16) return null;
        if (age < 1) return new PediatricBand(100, 160, 30, 60, 70);
        if (age < 4) return new PediatricBand(90, 150, 24, 40, 75);
        if (age < 6) return new PediatricBand(80, 140, 22, 34, 80);
        if (age < 12) return new PediatricBand(70, 120, 18, 30, 85);
        return new PediatricBand(60, 110, 12, 24, 90);
    }

    int score(int rr, int spo2, double temp, int sbp, int hr) {
        int s = deviation(hr, hrMin, hrMax) + deviation(rr, rrMin, rrMax);
        if (spo2 <= 91) s += 3;
        else if (spo2 <= 93) s += 2;
        else if (spo2 <= 95) s += 1;
        if (temp <= 35) s += 3;
        else if (temp <= 36) s += 1;
        else if (temp > 39) s += 2;
        else if (temp > 38) s += 1;
        if (sbp < sbpMin) s += 3;
        else if (sbp < sbpMin + 10) s += 1;
        return s;
    }

    /** Signos tan alejados de lo normal para la edad que el niño necesita reanimación. */
    boolean extreme(int hr, int rr, int sbp) {
        return hr > hrMax * 1.3 || hr < hrMin * 0.7 || rr > rrMax * 1.5 || rr < rrMin * 0.5 || sbp < sbpMin - 10;
    }

    private static int deviation(int value, int min, int max) {
        if (value >= min && value <= max) return 0;
        double off = value > max ? (value - max) / (double) max : (min - value) / (double) min;
        if (off <= 0.15) return 1;
        if (off <= 0.30) return 2;
        return 3;
    }
}
