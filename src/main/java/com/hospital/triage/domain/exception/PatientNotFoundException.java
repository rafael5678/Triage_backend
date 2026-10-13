package com.hospital.triage.domain.exception;

public class PatientNotFoundException extends RuntimeException {
    public PatientNotFoundException(String id) {
        super("Patient not found: " + id);
    }
}
