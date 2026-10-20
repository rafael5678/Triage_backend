package com.hospital.triage.ai.api;

import com.hospital.triage.ai.application.service.GeminiTriageService;
import com.hospital.triage.common.dto.AiAssistDTO;
import com.hospital.triage.common.dto.AnalyzeTriageDTO;
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
        return gemini.assist(
                dto.getConditionId(),
                dto.getReason(),
                dto.getHistory(),
                dto.getAge() != null ? dto.getAge() : 30,
                dto.getSex(),
                dto.getHeartRate() != null ? dto.getHeartRate() : 80,
                dto.getSpo2() != null ? dto.getSpo2() : 98,
                dto.getSystolic() != null ? dto.getSystolic() : 120,
                dto.getDiastolic() != null ? dto.getDiastolic() : 80,
                dto.getTemperature(),
                dto.getRespiratoryRate() != null ? dto.getRespiratoryRate() : 16,
                50.0,
                0,
                dto.getLocalModelScore()
        );
    }
}
