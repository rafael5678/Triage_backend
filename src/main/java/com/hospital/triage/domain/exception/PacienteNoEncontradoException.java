package com.hospital.triage.domain.exception;

public class PacienteNoEncontradoException extends RuntimeException {
    public PacienteNoEncontradoException(String id) {
        super("Paciente no encontrado: " + id);
    }
}
