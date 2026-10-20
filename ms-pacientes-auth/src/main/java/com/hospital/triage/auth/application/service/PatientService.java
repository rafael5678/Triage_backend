package com.hospital.triage.auth.application.service;

import com.hospital.triage.auth.domain.entity.Patient;
import com.hospital.triage.auth.infrastructure.persistence.PatientRepository;
import com.hospital.triage.common.dto.PatientIntakeDTO;
import com.hospital.triage.common.exception.PatientNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PatientService {

    private final PatientRepository patients;

    @Transactional
    public Patient register(PatientIntakeDTO dto) {
        // Si el paciente ya existe por documento, retornarlo o actualizarlo
        Optional<Patient> existing = patients.findByDocument(dto.getDocument().trim());
        if (existing.isPresent()) {
            Patient p = existing.get();
            p.setName(dto.getName().trim());
            p.setAge(dto.getAge());
            p.setSex(dto.getSex());
            p.setConditionId(dto.getConditionId());
            p.setReason(dto.getReason());
            p.setHistory(dto.getHistory());
            return patients.save(p);
        }

        Patient p = new Patient();
        p.setName(dto.getName().trim());
        p.setDocument(dto.getDocument().trim());
        p.setAge(dto.getAge());
        p.setSex(dto.getSex());
        p.setConditionId(dto.getConditionId());
        p.setReason(dto.getReason());
        p.setHistory(dto.getHistory());
        p.setCreatedAt(OffsetDateTime.now());
        return patients.save(p);
    }

    public Patient findById(UUID id) {
        return patients.findById(id)
                .orElseThrow(() -> new PatientNotFoundException(id.toString()));
    }

    public Optional<Patient> findByDocument(String document) {
        return patients.findByDocument(document.trim());
    }

    public List<Patient> listAll() {
        return patients.findAll();
    }
}
