package com.hospital.triage.api;

import com.hospital.triage.application.dto.AnalyzeTriageDTO;
import com.hospital.triage.application.dto.PatientIntakeDTO;
import com.hospital.triage.application.dto.QueueStateDTO;
import com.hospital.triage.application.dto.RankedPatientDTO;
import com.hospital.triage.application.dto.ReassessmentDTO;
import com.hospital.triage.application.dto.VitalSignsDTO;
import com.hospital.triage.application.service.GeminiTriageService;
import com.hospital.triage.application.service.QueueService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/triage")
@RequiredArgsConstructor
public class TriageController {

    private final QueueService queue;
    private final GeminiTriageService gemini;

    @GetMapping({"/health", "/salud"})
    public Map<String, String> health() {
        return Map.of(
                "status", "ok",
                "service", "hospital-triaje-api",
                "ai", gemini.configured() ? "gemini" : "inactive"
        );
    }

    @PostMapping({"/analyze", "/analizar"})
    public RankedPatientDTO analyze(@Valid @RequestBody AnalyzeTriageDTO dto) {
        return queue.analyze(dto);
    }

    @GetMapping({"/patients", "/pacientes"})
    public List<RankedPatientDTO> patients() {
        return queue.all();
    }

    @PostMapping({"/patients", "/pacientes"})
    @ResponseStatus(HttpStatus.CREATED)
    public RankedPatientDTO register(@Valid @RequestBody PatientIntakeDTO dto) {
        return queue.registerPatient(dto);
    }

    @PostMapping({"/evaluations", "/evaluaciones"})
    public RankedPatientDTO evaluate(@Valid @RequestBody VitalSignsDTO dto) {
        return queue.evaluate(dto);
    }

    @PutMapping({"/evaluations/{patientId}/reassess", "/evaluaciones/{patientId}/reevaluar"})
    public RankedPatientDTO reassess(@PathVariable UUID patientId, @Valid @RequestBody ReassessmentDTO dto) {
        return queue.reassess(patientId, dto);
    }

    @GetMapping({"/queue", "/cola"})
    public QueueStateDTO queueState() {
        return queue.queue();
    }

    @PatchMapping({"/care/{patientId}", "/atender/{patientId}"})
    public RankedPatientDTO startCare(@PathVariable UUID patientId) {
        return queue.startCare(patientId);
    }
}
