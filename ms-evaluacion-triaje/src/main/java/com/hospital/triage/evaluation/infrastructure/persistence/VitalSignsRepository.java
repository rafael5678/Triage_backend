package com.hospital.triage.evaluation.infrastructure.persistence;

import com.hospital.triage.evaluation.domain.entity.VitalSigns;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface VitalSignsRepository extends JpaRepository<VitalSigns, UUID> {
    List<VitalSigns> findByPatient_IdOrderByRecordedAtDesc(UUID patientId);
}
