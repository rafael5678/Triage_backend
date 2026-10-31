package com.hospital.triage.ai.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.triage.ai.domain.entity.Symptom;
import com.hospital.triage.ai.infrastructure.persistence.SymptomRepository;
import com.hospital.triage.common.dto.IntakeRequest;
import com.hospital.triage.common.dto.IntakeSuggestion;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Convierte el relato (y la foto) del ingreso en el formulario de triaje. */
@Service
@RequiredArgsConstructor
public class IntakeService {

    static final List<String> VITALS = List.of("heartRate", "spo2", "systolic", "diastolic", "temperature", "respiratoryRate");

    private final GeminiClient gemini;
    private final SymptomRepository symptoms;
    private final ObjectMapper json;

    public IntakeSuggestion suggest(IntakeRequest request) {
        if (!gemini.configured()) {
            return IntakeSuggestion.builder().active(false).build();
        }
        List<Symptom> catalog = symptoms.findByActiveTrueOrderBySeverityDesc();
        Map<String, Object> known = request.getKnown() == null ? Map.of() : request.getKnown();
        boolean hasPhoto = GeminiClient.cleanBase64(request.getPhotoBase64()) != null;
        var answer = gemini.generate(prompt(request, catalog, known, hasPhoto), request.getPhotoBase64(), request.getPhotoMime(), 512);
        if (answer.isEmpty()) {
            return IntakeSuggestion.builder().active(false).build();
        }
        return normalize(answer.get(), catalog, request, known, hasPhoto);
    }

    IntakeSuggestion normalize(JsonNode a, List<Symptom> catalog, IntakeRequest request, Map<String, Object> known, boolean hasPhoto) {
        Set<String> catalogIds = catalog.stream().map(Symptom::getId).collect(Collectors.toSet());
        Set<String> comorbidityIds = request.getComorbidityOptions().stream()
                .map(IntakeRequest.Option::getId).collect(Collectors.toSet());

        String conditionId = GeminiClient.textOrNull(a.path("conditionId"));
        if (conditionId != null && !catalogIds.contains(conditionId)) {
            conditionId = null;
        }
        List<String> comorbidities = new ArrayList<>();
        for (String id : GeminiClient.texts(a.path("comorbidityIds"), 10)) {
            if (comorbidityIds.contains(id)) {
                comorbidities.add(id);
            }
        }

        Set<String> estimated = new LinkedHashSet<>();
        for (String field : GeminiClient.texts(a.path("estimatedFields"), 10)) {
            if (VITALS.contains(field) && !isKnown(known, field)) {
                estimated.add(field);
            }
        }

        IntakeSuggestion.IntakeSuggestionBuilder b = IntakeSuggestion.builder()
                .active(true)
                .conditionId(conditionId)
                .reason(GeminiClient.textOrNull(a.path("reason")))
                .comorbidityIds(comorbidities)
                .age(clamp(a.path("age"), 0, 120))
                .sex(sex(GeminiClient.textOrNull(a.path("sex"))))
                .heartRate(clamp(a.path("heartRate"), 20, 250))
                .spo2(clamp(a.path("spo2"), 50, 100))
                .systolic(clamp(a.path("systolic"), 50, 260))
                .diastolic(clamp(a.path("diastolic"), 20, 160))
                .temperature(temperature(a.path("temperature")))
                .respiratoryRate(clamp(a.path("respiratoryRate"), 4, 60))
                .glucose(clamp(a.path("glucose"), 10, 1500))
                .painScale(clamp(a.path("painScale"), 0, 10))
                .consciousness(avpu(GeminiClient.textOrNull(a.path("consciousness"))))
                .photoFindings(hasPhoto ? GeminiClient.textOrNull(a.path("photoFindings")) : null)
                .notes(GeminiClient.texts(a.path("notes"), 5));

        IntakeSuggestion s = b.build();
        // Si Gemini inventó un signo (lo marcó estimado y nadie lo escribió), se descarta.
        s.setHeartRate(keepVital(s.getHeartRate(), "heartRate", known, estimated));
        s.setSpo2(keepVital(s.getSpo2(), "spo2", known, estimated));
        s.setSystolic(keepVital(s.getSystolic(), "systolic", known, estimated));
        s.setDiastolic(keepVital(s.getDiastolic(), "diastolic", known, estimated));
        s.setTemperature(keepVital(s.getTemperature(), "temperature", known, estimated));
        s.setRespiratoryRate(keepVital(s.getRespiratoryRate(), "respiratoryRate", known, estimated));
        estimated.removeIf(field -> vital(s, field) == null);
        s.setEstimatedFields(new ArrayList<>(estimated));
        return s;
    }

    private String prompt(IntakeRequest request, List<Symptom> catalog, Map<String, Object> known, boolean hasPhoto) {
        String catalogText = catalog.stream()
                .map(s -> s.getId() + " = " + s.getLabel() + " (gravedad " + s.getSeverity() + ")")
                .collect(Collectors.joining("\n- "));
        String comorbidities = request.getComorbidityOptions().stream()
                .map(o -> o.getId() + " = " + o.getLabel())
                .collect(Collectors.joining("\n- "));
        String knownText;
        try {
            knownText = json.writeValueAsString(known);
        } catch (Exception ex) {
            knownText = "{}";
        }
        return """
                Eres enfermera jefe de triage de urgencias en Colombia. Con el relato%s llena el formulario de ingreso.
                Reglas:
                - conditionId: SOLO uno de estos ids del catálogo (el motivo más grave):
                - %s
                - comorbidityIds: SOLO ids de esta lista, si el relato los menciona:
                - %s
                - reason: resumen corto en español sencillo (máx 200 caracteres).
                - Signos vitales (heartRate, spo2, systolic, diastolic, temperature, respiratoryRate):
                  SOLO si el relato o DATOS YA ESCRITOS traen el número. Si no se midió, usa null.
                  NUNCA inventes oxígeno bajo, pulso raro ni presión de shock. Un dolor de cabeza no es un paro.
                - estimatedFields: vacío, salvo que copies un número dudoso del relato.
                - Nunca cambies los DATOS YA ESCRITOS.
                - consciousness: A, C, V, P o U solo si el relato lo deja claro; si no, A.
                - glucose y painScale solo si se mencionan.
                - notes: máximo 3 avisos prácticos (qué medir).

                Responde SOLO este JSON:
                {"conditionId":"","reason":"","comorbidityIds":[],"age":null,"sex":null,
                 "heartRate":null,"spo2":null,"systolic":null,"diastolic":null,"temperature":null,"respiratoryRate":null,
                 "glucose":null,"painScale":null,"consciousness":"A",
                 "estimatedFields":[],
                 "photoFindings":"%s",
                 "notes":[]}

                DATOS YA ESCRITOS: %s
                RELATO: %s
                """.formatted(
                hasPhoto ? " y la foto adjunta" : "",
                catalogText,
                comorbidities.isBlank() ? "(sin lista)" : comorbidities,
                hasPhoto ? "qué se ve en la foto y qué tan grave parece" : "",
                knownText,
                request.getStory() == null ? "" : request.getStory()
        );
    }

    private static <T> T keepVital(T value, String field, Map<String, Object> known, Set<String> estimated) {
        if (isKnown(known, field)) {
            return value;
        }
        if (estimated.contains(field)) {
            return null;
        }
        return value;
    }

    private static boolean isKnown(Map<String, Object> known, String field) {
        Object value = known.get(field);
        return value != null && !String.valueOf(value).isBlank();
    }

    private static Object vital(IntakeSuggestion s, String field) {
        return switch (field) {
            case "heartRate" -> s.getHeartRate();
            case "spo2" -> s.getSpo2();
            case "systolic" -> s.getSystolic();
            case "diastolic" -> s.getDiastolic();
            case "temperature" -> s.getTemperature();
            case "respiratoryRate" -> s.getRespiratoryRate();
            default -> null;
        };
    }

    private static Integer clamp(JsonNode node, int min, int max) {
        if (node == null || !node.isNumber()) {
            return null;
        }
        int value = (int) Math.round(node.asDouble());
        return Math.max(min, Math.min(max, value));
    }

    private static BigDecimal temperature(JsonNode node) {
        if (node == null || !node.isNumber()) {
            return null;
        }
        double value = Math.max(30.0, Math.min(43.0, node.asDouble()));
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP);
    }

    private static String sex(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim().toUpperCase();
        return s.equals("M") || s.equals("F") || s.equals("O") ? s : null;
    }

    private static String avpu(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim().toUpperCase();
        return s.length() == 1 && "ACVPU".contains(s) ? s : null;
    }
}
