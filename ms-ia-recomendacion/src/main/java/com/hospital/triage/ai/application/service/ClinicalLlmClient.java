/** ClinicalLlmClient: Gemini first, Claude Haiku only if Gemini returns empty. */
package com.hospital.triage.ai.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.Optional;

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
        if (g && h) {
            return "gemini+haiku";
        }
        if (g) {
            return "gemini";
        }
        if (h) {
            return "haiku";
        }
        return "inactive";
    }

    public Optional<JsonNode> generate(String prompt, String photoBase64, String photoMime, int maxOutputTokens) {
        if (gemini.configured()) {
            Optional<JsonNode> first = gemini.generate(prompt, photoBase64, photoMime, maxOutputTokens);
            if (first.isPresent()) {
                return first;
            }
        }
        if (anthropic != null && anthropic.configured()) {
            return anthropic.generate(prompt, photoBase64, photoMime, maxOutputTokens);
        }
        return Optional.empty();
    }
}
