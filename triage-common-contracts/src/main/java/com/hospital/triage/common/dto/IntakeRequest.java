package com.hospital.triage.common.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Free-text intake story; the LLM turns it into the triage form. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IntakeRequest {
    @Size(max = 4000)
    private String story;

    @Size(max = 4_000_000)
    private String photoBase64;

    @Size(max = 40)
    private String photoMime;

    /** What staff already typed; the LLM must not overwrite it. */
    private Map<String, Object> known;

    /** Comorbidities shown on the form (id and label) so the LLM can tick the same ones. */
    @Builder.Default
    private List<Option> comorbidityOptions = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Option {
        private String id;
        private String label;
    }
}
