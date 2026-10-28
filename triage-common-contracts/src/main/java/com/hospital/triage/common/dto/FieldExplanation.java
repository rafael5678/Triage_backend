package com.hospital.triage.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FieldExplanation {
    private boolean active;
    private String whatIs;
    private String howToMeasure;
    private String normalRange;
    private String valueMeaning;
    private String whatToDo;
}
