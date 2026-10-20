package com.hospital.triage.evaluation.infrastructure.persistence;

import com.hospital.triage.evaluation.domain.entity.Patient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PatientRepository extends JpaRepository<Patient, UUID> {
    Optional<Patient> findByDocument(String document);
}
