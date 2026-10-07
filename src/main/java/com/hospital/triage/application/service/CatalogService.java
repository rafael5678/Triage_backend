package com.hospital.triage.application.service;

import com.hospital.triage.application.dto.SymptomRequest;
import com.hospital.triage.domain.entity.Symptom;
import com.hospital.triage.infrastructure.persistence.SymptomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class CatalogService {

    private final SymptomRepository symptoms;

    public List<Symptom> listActive() {
        return symptoms.findByActiveTrueOrderBySeverityDesc();
    }

    public List<Symptom> listAll() {
        return symptoms.findAllByOrderBySeverityDesc();
    }

    @Transactional
    public Symptom save(SymptomRequest request) {
        String id = request.getId() == null || request.getId().isBlank()
                ? slug(request.getLabel())
                : slug(request.getId());
        Symptom row = symptoms.findById(id).orElseGet(Symptom::new);
        row.setId(id);
        row.setLabel(request.getLabel().trim());
        row.setHint(request.getHint() == null ? "" : request.getHint().trim());
        row.setSeverity(request.getSeverity());
        row.setActive(request.getActive() == null || request.getActive());
        return symptoms.save(row);
    }

    @Transactional
    public void delete(String id) {
        symptoms.deleteById(id);
    }

    public static String slug(String raw) {
        String n = Normalizer.normalize(raw, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        n = n.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
        n = n.replaceAll("(^-|-$)", "");
        return n.isBlank() ? "sintoma" : n.substring(0, Math.min(60, n.length()));
    }
}
