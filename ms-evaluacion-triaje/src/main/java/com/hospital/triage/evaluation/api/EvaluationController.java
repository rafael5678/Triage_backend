/** EvaluationController: application type for the triage hospital services. */
package com.hospital.triage.evaluation.api;

import com.hospital.triage.common.dto.AnalyzeTriageDTO;
import com.hospital.triage.common.dto.RankedPatientDTO;
import com.hospital.triage.common.dto.TriageStatsDTO;
import com.hospital.triage.common.dto.ReassessmentDTO;
import com.hospital.triage.common.dto.VitalSignsDTO;
import com.hospital.triage.evaluation.application.service.EvaluationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import com.hospital.triage.common.enums.CareStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/triage")
@RequiredArgsConstructor
public class EvaluationController {

    private final EvaluationService evaluationService;

    @GetMapping({"/health", "/salud", "/evaluations/health", "/evaluaciones/salud"})
    public Map<String, String> health() {
        return Map.of(
                "status", "ok",
                "service", "ms-evaluacion-triaje",
                "port", "8082",
                "ai", evaluationService.aiAvailable() ? "grpc" : "inactive"
        );
    }

    @PostMapping({"/analyze", "/analizar"})
    public RankedPatientDTO analyze(@Valid @RequestBody AnalyzeTriageDTO dto) {
        return evaluationService.analyze(dto);
    }

    @GetMapping({"/evaluations/stats", "/evaluaciones/estadisticas"})
    public TriageStatsDTO stats() {
        return evaluationService.stats();
    }

    @PostMapping({"/evaluations", "/evaluaciones"})
    public RankedPatientDTO evaluate(@Valid @RequestBody VitalSignsDTO dto) {
        return evaluationService.evaluate(dto);
    }

    @PutMapping({"/evaluations/{patientId}/reassess", "/evaluaciones/{patientId}/reevaluar"})
    public RankedPatientDTO reassess(@PathVariable("patientId") UUID patientId, @Valid @RequestBody ReassessmentDTO dto) {
        return evaluationService.reassess(patientId, dto);
    }

    @PatchMapping({"/evaluations/{patientId}/status", "/evaluaciones/{patientId}/estado"})
    public RankedPatientDTO updateStatus(@PathVariable("patientId") UUID patientId,
                                         @RequestParam("status") CareStatus status) {
        return evaluationService.updateStatus(patientId, status);
    }

    /** Every measurement for the patient, first to last, for the timeline. */
    @GetMapping({"/patients/{patientId}/history", "/pacientes/{patientId}/historial"})
    public java.util.List<RankedPatientDTO> history(@PathVariable("patientId") UUID patientId) {
        return evaluationService.history(patientId);
    }
}
