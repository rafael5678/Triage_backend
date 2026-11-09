/** PatientNotFoundException: application type for the triage hospital services. */
package com.hospital.triage.common.exception;

public class PatientNotFoundException extends RuntimeException {
    public PatientNotFoundException(String patientId) {
        super("Patient not found: " + patientId);
    }
}
