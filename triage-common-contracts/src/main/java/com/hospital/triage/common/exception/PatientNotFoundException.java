package com.hospital.triage.common.exception;

public class PatientNotFoundException extends RuntimeException {
    public PatientNotFoundException(String patientId) {
        super("Patient not found: " + patientId);
    }
}
