package com.hospital.triage.auth.infrastructure.persistence;

import com.hospital.triage.auth.domain.entity.Patient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PatientRepository extends JpaRepository<Patient, UUID> {
    Optional<Patient> findByDocument(String document);
    boolean existsByDocument(String document);
}
