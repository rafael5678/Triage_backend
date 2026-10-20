package com.hospital.triage.evaluation.api;

import com.hospital.triage.common.dto.AnalyzeTriageDTO;
import com.hospital.triage.common.dto.RankedPatientDTO;
import com.hospital.triage.common.dto.ReassessmentDTO;
import com.hospital.triage.common.dto.VitalSignsDTO;
import com.hospital.triage.evaluation.application.service.EvaluationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/triage")
@RequiredArgsConstructor
public class EvaluationController {

    private final EvaluationService evaluationService;

    @GetMapping({"/health", "/salud"})
    public Map<String, String> health() {
        return Map.of(
                "status", "ok",
                "service", "ms-evaluacion-triaje",
                "port", "8082",
                "ai", evaluationService.isGeminiConfigured() ? "gemini" : "inactive"
        );
    }

    @PostMapping({"/analyze", "/analizar"})
    public RankedPatientDTO analyze(@Valid @RequestBody AnalyzeTriageDTO dto) {
        return evaluationService.analyze(dto);
    }

    @PostMapping({"/evaluations", "/evaluaciones"})
    public RankedPatientDTO evaluate(@Valid @RequestBody VitalSignsDTO dto) {
        return evaluationService.evaluate(dto);
    }

    @PutMapping({"/evaluations/{patientId}/reassess", "/evaluaciones/{patientId}/reevaluar"})
    public RankedPatientDTO reassess(@PathVariable UUID patientId, @Valid @RequestBody ReassessmentDTO dto) {
        return evaluationService.reassess(patientId, dto);
    }
}
