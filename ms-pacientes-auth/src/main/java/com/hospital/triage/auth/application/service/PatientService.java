package com.hospital.triage.auth.application.service;

import com.hospital.triage.auth.domain.entity.Patient;
import com.hospital.triage.auth.infrastructure.persistence.PatientRepository;
import com.hospital.triage.common.ColombianMobile;
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

    /** Si el documento ya existe se actualiza la ficha: es la misma persona que vuelve a consultar. */
    @Transactional
    public Patient register(PatientIntakeDTO dto) {
        Patient p = patients.findByDocument(dto.getDocument().trim()).orElseGet(() -> {
            Patient created = new Patient();
            created.setDocument(dto.getDocument().trim());
            created.setCreatedAt(OffsetDateTime.now());
            return created;
        });
        p.setName(dto.getName().trim());
        p.setAge(dto.getAge());
        p.setSex(dto.getSex());
        p.setConditionId(dto.getConditionId());
        p.setReason(dto.getReason());
        p.setHistory(dto.getHistory());
        p.setPhone(ColombianMobile.normalize(dto.getPhone()));
        boolean pregnant = Boolean.TRUE.equals(dto.getPregnant()) && "F".equalsIgnoreCase(dto.getSex());
        p.setPregnant(pregnant);
        p.setGestationWeeks(pregnant ? dto.getGestationWeeks() : null);
        return patients.save(p);
    }

    /** Deja el celular en formato 57 + 10 dígitos, o null. Rechaza números inventados. */
    static String normalizePhone(String raw) {
        return ColombianMobile.normalize(raw);
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
