package com.hospital.triage.auth.api;

import com.hospital.triage.auth.application.service.PatientService;
import com.hospital.triage.auth.domain.entity.Patient;
import com.hospital.triage.common.dto.PatientIntakeDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PatientController {

    private final PatientService patientService;

    @PostMapping({"/patients", "/pacientes", "/triage/patients", "/triage/pacientes"})
    @ResponseStatus(HttpStatus.CREATED)
    public Patient register(@Valid @RequestBody PatientIntakeDTO dto) {
        return patientService.register(dto);
    }

    @GetMapping({"/patients", "/pacientes"})
    public List<Patient> listAll() {
        return patientService.listAll();
    }

    @GetMapping({"/patients/{id}", "/pacientes/{id}"})
    public Patient getById(@PathVariable UUID id) {
        return patientService.findById(id);
    }

    @GetMapping({"/patients/document/{document}", "/pacientes/documento/{document}"})
    public ResponseEntity<Patient> getByDocument(@PathVariable String document) {
        return patientService.findByDocument(document)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
