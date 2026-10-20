package com.hospital.triage.infrastructure.persistence;

import com.hospital.triage.domain.entity.VitalSigns;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface VitalSignsRepository extends JpaRepository<VitalSigns, UUID> {
}
