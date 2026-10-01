package com.hospital.triage.infrastructure.persistence;

import com.hospital.triage.domain.entity.Paciente;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/** Listo para PostgreSQL cuando se active el datasource. */
public interface PacienteRepository extends JpaRepository<Paciente, UUID> {
    Optional<Paciente> findByDocumento(String documento);
}
