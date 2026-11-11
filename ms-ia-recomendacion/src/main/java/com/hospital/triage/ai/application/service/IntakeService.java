package com.hospital.triage.ai.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.triage.ai.domain.entity.Symptom;
import com.hospital.triage.ai.infrastructure.persistence.SymptomRepository;
import com.hospital.triage.common.dto.IntakeRequest;
import com.hospital.triage.common.dto.IntakeSuggestion;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Turns the intake story (and photo) into the triage form. */
@Service
public class IntakeService {

    static final List<String> VITALS = List.of("heartRate", "spo2", "systolic", "diastolic", "temperature", "respiratoryRate");

    private final ClinicalLlmClient llm;
    private final SymptomRepository symptoms;
    private final ObjectMapper json;

    public IntakeService(GeminiClient gemini, SymptomRepository symptoms, ObjectMapper json) {
        this(new ClinicalLlmClient(gemini, null), symptoms, json);
    }

    @Autowired
    public IntakeService(ClinicalLlmClient llm, SymptomRepository symptoms, ObjectMapper json) {
        this.llm = llm;
        this.symptoms = symptoms;
        this.json = json;
    }

    public IntakeSuggestion suggest(IntakeRequest request) {
        if (!llm.anyConfigured()) {
            return IntakeSuggestion.builder().active(false).build();
        }
        List<Symptom> catalog = symptoms.findByActiveTrueOrderBySeverityDesc();
        Map<String, Object> known = request.getKnown() == null ? Map.of() : request.getKnown();
        boolean hasPhoto = GeminiClient.cleanBase64(request.getPhotoBase64()) != null;
        var answer = llm.generate(
                prompt(request, catalog, known, hasPhoto),
                request.getPhotoBase64(),
                request.getPhotoMime(),
                hasPhoto ? 1200 : 800,
                IntakeService::looksLikeIntake);
        if (answer.isEmpty()) {
            IntakeSuggestion fallback = fromStory(request.getStory());
            fallback.setNotes(List.of(
                    llm.lastError().isBlank()
                            ? "La IA no contestó. Se armó el formulario con el relato; tome los signos a mano."
                            : "IA: " + llm.lastError()));
            return fallback;
        }
        return normalize(answer.get(), catalog, request, known, hasPhoto);
    }

    static IntakeSuggestion fromStory(String story) {
        String t = story == null ? "" : story.toLowerCase();
        String conditionId = "cura";
        if (t.contains("no responde") || t.contains("desmay")) {
            conditionId = "paro";
        } else if (t.contains("derrame") || t.contains("boca torcida") || t.contains("no habla")) {
            conditionId = "avc";
        } else if (t.contains("pecho")) {
            conditionId = "sca";
        } else if (t.contains("picadura") || t.contains("abeja") || t.contains("avispa") || t.contains("ojo")) {
            conditionId = "cura";
        } else if (t.contains("choque") || t.contains("altura")) {
            conditionId = "trauma";
        }
        Integer age = null;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d{1,3})\\s*años").matcher(t);
        if (m.find()) {
            age = Integer.parseInt(m.group(1));
        }
        String sex = t.contains("señora") || t.contains("mujer") || t.contains("embaraz") ? "F"
                : (t.contains("señor") || t.contains("hombre") ? "M" : null);
        return IntakeSuggestion.builder()
                .active(true)
                .conditionId(conditionId)
                .reason(story == null ? "" : story.substring(0, Math.min(200, story.length())))
                .age(age)
                .sex(sex)
                .build();
    }

    static boolean looksLikeIntake(JsonNode node) {
        if (node == null || node.isMissingNode()) {
            return false;
        }
        if (node.has("score") && !node.has("conditionId") && !node.has("age")) {
            return false;
        }
        return node.has("conditionId") || node.has("reason") || node.has("age")
                || node.has("heartRate") || node.has("notes") || node.has("photoFindings");
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
                .spo2(clamp(a.path("spo2"), 0, 190))
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
        // Drop a vital the LLM invented (marked estimated and nobody typed it).
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
                - conditionId: SOLO uno de estos ids. Elige por lo que DESCRIBE el paciente, no por una palabra suelta.
                  “me picó una abeja / avispa en el ojo” → cura (herida/picadura), NO trauma.
                  “se me torció la cara o no habla” → avc. “dolor de pecho” → sca.
                  trauma SOLO si hay choque, caída de altura o politrauma.
                - %s
                - comorbidityIds: SOLO ids de esta lista, si el relato los menciona:
                - %s
                - reason: resumen corto en español sencillo (máx 200 caracteres). Incluye lo grave que se vea en la foto.
                - Signos vitales (heartRate, spo2, systolic, diastolic, temperature, respiratoryRate):
                  SOLO si el relato o DATOS YA ESCRITOS traen el número. Si no se midió, usa null.
                  Copia spo2 aunque pase de 100 (hasta 190) y heartRate hasta 250. No recortes a 100.
                  NUNCA inventes oxígeno bajo, pulso raro ni presión de shock. Un dolor de cabeza no es un paro.
                - estimatedFields: vacío, salvo que copies un número dudoso del relato.
                - Edad y sexo: salen del RELATO (niña de 7, joven de 9, señora de 50). No uses una edad de otro paciente.
                - Nunca cambies los DATOS YA ESCRITOS de signos medidos.
                - consciousness: A, C, V, P o U solo si el relato lo deja claro; si no, A.
                - glucose y painScale solo si se mencionan.
                - Si hay foto: mírala con detalle (herida, sangrado, color de piel/labios, hinchazón, quemadura, humedad, esfuerzo al respirar). No inventes signos vitales desde la foto.
                - notes: máximo 4 avisos prácticos (qué medir y qué alerta ves).

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
                hasPhoto ? "3 a 5 frases de lo que VES (no lo que imaginas) y qué tan grave parece" : "",
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
