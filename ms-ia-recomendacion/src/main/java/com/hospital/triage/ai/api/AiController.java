package com.hospital.triage.ai.api;

import com.hospital.triage.ai.application.service.ClinicalCase;
import com.hospital.triage.ai.application.service.FieldExplainer;
import com.hospital.triage.ai.application.service.GeminiTriageService;
import com.hospital.triage.ai.application.service.IntakeService;
import com.hospital.triage.common.dto.AiAssistDTO;
import com.hospital.triage.common.dto.AnalyzeTriageDTO;
import com.hospital.triage.common.dto.FieldExplanation;
import com.hospital.triage.common.dto.FieldExplanationRequest;
import com.hospital.triage.common.dto.IntakeRequest;
import com.hospital.triage.common.dto.IntakeSuggestion;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AiController {

    private final GeminiTriageService gemini;
    private final IntakeService intake;
    private final FieldExplainer explainer;

    @GetMapping({"/ia/health", "/triage/health", "/salud"})
    public Map<String, String> health() {
        return Map.of(
                "status", "ok",
                "service", "ms-ia-recomendacion",
                "port", "8084",
                "ai", gemini.configured() ? "gemini" : "inactive"
        );
    }

    @PostMapping({"/ia/assist", "/triage/assist"})
    public AiAssistDTO assist(@Valid @RequestBody AnalyzeTriageDTO dto) {
        return gemini.assist(ClinicalCase.builder()
                .conditionId(dto.getConditionId())
                .reason(dto.getReason())
                .history(dto.getHistory())
                .age(dto.getAge() != null ? dto.getAge() : 30)
                .sex(dto.getSex())
                .heartRate(dto.getHeartRate())
                .spo2(dto.getSpo2())
                .systolic(dto.getSystolic())
                .diastolic(dto.getDiastolic())
                .temperature(dto.getTemperature())
                .respiratoryRate(dto.getRespiratoryRate())
                .glucose(dto.getGlucose())
                .painScale(dto.getPainScale())
                .consciousness(dto.getConsciousness())
                .estimatedFields(dto.getEstimatedFields())
                .photoBase64(dto.getPhotoBase64())
                .photoMime(dto.getPhotoMime())
                .protocolScore(50.0)
                .news2(0)
                .localModelScore(dto.getLocalModelScore())
                .build());
    }

    @PostMapping({"/ia/intake", "/ia/ingreso"})
    public IntakeSuggestion intake(@Valid @RequestBody IntakeRequest request) {
        return intake.suggest(request);
    }

    @PostMapping({"/ia/explain", "/ia/explicar"})
    public FieldExplanation explain(@Valid @RequestBody FieldExplanationRequest request) {
        return explainer.explain(request);
    }
}
