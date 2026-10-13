package com.hospital.triage.infrastructure.persistence;

import com.hospital.triage.domain.entity.Patient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PatientRepository extends JpaRepository<Patient, UUID> {
    Optional<Patient> findByDocument(String document);
}
