package com.hospital.triage.domain.exception;

public class InvalidVitalSignsException extends RuntimeException {
    public InvalidVitalSignsException(String message) {
        super(message);
    }
}
