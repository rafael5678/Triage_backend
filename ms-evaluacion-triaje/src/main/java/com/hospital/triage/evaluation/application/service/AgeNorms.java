package com.hospital.triage.evaluation.application.service;

/** Rangos de reposo según la edad. Un pulso de 140 es normal en un bebé, no a los 20 años. */
record AgeNorms(int hrMin, int hrMax, int rrMin, int rrMax, int sbpMin, int sbpMax) {

    static AgeNorms of(int age) {
        if (age < 1) return new AgeNorms(100, 160, 30, 60, 70, 100);
        if (age < 4) return new AgeNorms(90, 150, 24, 40, 75, 110);
        if (age < 6) return new AgeNorms(80, 140, 22, 34, 80, 115);
        if (age < 12) return new AgeNorms(70, 120, 18, 30, 85, 120);
        if (age < 16) return new AgeNorms(60, 110, 12, 24, 90, 125);
        if (age < 30) return new AgeNorms(60, 100, 12, 20, 90, 120);
        if (age < 45) return new AgeNorms(60, 100, 12, 20, 95, 130);
        if (age < 60) return new AgeNorms(60, 95, 12, 20, 100, 135);
        if (age < 70) return new AgeNorms(60, 90, 12, 22, 100, 145);
        return new AgeNorms(55, 90, 12, 22, 100, 150);
    }

    boolean hrOff(int hr) {
        return hr < hrMin || hr > hrMax;
    }

    boolean hrFar(int hr) {
        return hr < hrMin * 0.7 || hr > hrMax * 1.3;
    }

    boolean rrOff(int rr) {
        return rr < rrMin || rr > rrMax;
    }
}
