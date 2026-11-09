package com.hospital.triage.ai.application.service;

import com.hospital.triage.ai.domain.entity.Symptom;
import com.hospital.triage.ai.infrastructure.persistence.SymptomRepository;
import com.hospital.triage.common.dto.SymptomRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class CatalogService {

    private final SymptomRepository symptoms;

    public List<Symptom> listActive() {
        return newestFirst(symptoms.findByActiveTrueOrderBySeverityDesc());
    }

    public List<Symptom> listAll() {
        return newestFirst(symptoms.findAllByOrderBySeverityDesc());
    }

    @Transactional
    public Symptom save(SymptomRequest request) {
        String id = request.getId() == null || request.getId().isBlank()
                ? slug(request.getLabel())
                : slug(request.getId());
        Symptom row = symptoms.findById(id).orElseGet(Symptom::new);
        boolean created = row.getId() == null;
        row.setId(id);
        row.setLabel(request.getLabel().trim());
        row.setHint(request.getHint() == null ? "" : request.getHint().trim());
        row.setSeverity(request.getSeverity());
        row.setActive(request.getActive() == null || request.getActive());
        if (created) {
            row.setCreatedAt(OffsetDateTime.now());
        }
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

    private static List<Symptom> newestFirst(List<Symptom> rows) {
        return rows.stream()
                .sorted(Comparator
                        .comparing(Symptom::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(Comparator.comparingInt(Symptom::getSeverity).reversed()))
                .toList();
    }
}
