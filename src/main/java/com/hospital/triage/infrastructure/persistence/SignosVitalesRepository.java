package com.hospital.triage.infrastructure.persistence;

import com.hospital.triage.domain.entity.SignosVitales;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SignosVitalesRepository extends JpaRepository<SignosVitales, UUID> {
}
