/** ClinicalLlmClient: Claude first (faster, better triage), Gemini only if Claude is empty. */
package com.hospital.triage.ai.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.function.Predicate;

@Component
public class ClinicalLlmClient {

    private final GeminiClient gemini;
    private final AnthropicClient anthropic;

    public ClinicalLlmClient(GeminiClient gemini, AnthropicClient anthropic) {
        this.gemini = gemini;
        this.anthropic = anthropic;
    }

    public boolean anyConfigured() {
        return gemini.configured() || (anthropic != null && anthropic.configured());
    }

    public String providerLabel() {
        boolean g = gemini.configured();
        boolean h = anthropic != null && anthropic.configured();
        if (h && g) {
            return "haiku+gemini";
        }
        if (h) {
            return "haiku";
        }
        if (g) {
            return "gemini";
        }
        return "inactive";
    }

    public Optional<JsonNode> generate(String prompt, String photoBase64, String photoMime, int maxOutputTokens) {
        return generate(prompt, photoBase64, photoMime, maxOutputTokens, null);
    }

    public Optional<JsonNode> generate(String prompt, String photoBase64, String photoMime, int maxOutputTokens,
                                       Predicate<JsonNode> accept) {
        if (anthropic != null && anthropic.configured()) {
            Optional<JsonNode> first = anthropic.generate(prompt, photoBase64, photoMime, maxOutputTokens);
            if (first.isPresent() && (accept == null || accept.test(first.get()))) {
                return first;
            }
        }
        if (gemini.configured()) {
            return gemini.generate(prompt, photoBase64, photoMime, maxOutputTokens);
        }
        return Optional.empty();
    }
}
