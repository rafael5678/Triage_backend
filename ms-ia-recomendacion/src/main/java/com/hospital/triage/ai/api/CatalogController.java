package com.hospital.triage.ai.api;

import com.hospital.triage.ai.application.service.CatalogService;
import com.hospital.triage.ai.domain.entity.Symptom;
import com.hospital.triage.common.dto.SymptomRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CatalogController {

    private final CatalogService catalog;

    @GetMapping({"/catalog/symptoms", "/catalogo/sintomas"})
    public List<Symptom> activeSymptoms() {
        return catalog.listActive();
    }

    @GetMapping({"/admin/symptoms", "/admin/sintomas"})
    public List<Symptom> allSymptoms() {
        return catalog.listAll();
    }

    @PostMapping({"/admin/symptoms", "/admin/sintomas"})
    @ResponseStatus(HttpStatus.CREATED)
    public Symptom saveSymptom(@Valid @RequestBody SymptomRequest request) {
        return catalog.save(request);
    }

    @DeleteMapping({"/admin/symptoms/{id}", "/admin/sintomas/{id}"})
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSymptom(@PathVariable String id) {
        catalog.delete(id);
    }
}
