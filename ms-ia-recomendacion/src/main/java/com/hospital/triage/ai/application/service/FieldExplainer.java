package com.hospital.triage.ai.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.hospital.triage.common.dto.FieldExplanation;
import com.hospital.triage.common.dto.FieldExplanationRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Explica en palabras simples un dato del formulario (qué es, cómo se mide y qué significa el valor). */
@Service
@RequiredArgsConstructor
public class FieldExplainer {

    private final GeminiClient gemini;

    public FieldExplanation explain(FieldExplanationRequest request) {
        if (!gemini.configured()) {
            return FieldExplanation.builder().active(false).build();
        }
        String prompt = """
                Explica a personal de admisión de urgencias (sin formación médica) este dato del formulario de triage.
                Español sencillo, frases cortas, sin jerga. Si hay un valor, di si es normal, bajo o alto y qué tan urgente es.
                Responde SOLO este JSON:
                {"whatIs":"qué es (1-2 frases)","howToMeasure":"con qué aparato o cómo se pregunta",
                 "normalRange":"rango normal en adultos","valueMeaning":"qué significa el valor dado (vacío si no hay valor)",
                 "whatToDo":"qué hacer si está alterado"}
                DATO: %s
                VALOR: %s
                CONTEXTO DEL PACIENTE: %s
                """.formatted(
                request.getField(),
                request.getValue() == null || request.getValue().isBlank() ? "(sin valor)" : request.getValue(),
                request.getContext() == null ? "" : request.getContext()
        );
        return gemini.generate(prompt, null, null, 1536)
                .map(this::toExplanation)
                .orElseGet(() -> FieldExplanation.builder().active(false).build());
    }

    private FieldExplanation toExplanation(JsonNode a) {
        return FieldExplanation.builder()
                .active(true)
                .whatIs(GeminiClient.textOrNull(a.path("whatIs")))
                .howToMeasure(GeminiClient.textOrNull(a.path("howToMeasure")))
                .normalRange(GeminiClient.textOrNull(a.path("normalRange")))
                .valueMeaning(GeminiClient.textOrNull(a.path("valueMeaning")))
                .whatToDo(GeminiClient.textOrNull(a.path("whatToDo")))
                .build();
    }
}
