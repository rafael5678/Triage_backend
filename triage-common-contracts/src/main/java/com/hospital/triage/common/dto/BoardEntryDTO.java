package com.hospital.triage.common.dto;

import com.hospital.triage.common.enums.CareStatus;
import com.hospital.triage.common.enums.TriageLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/** Fila de la pantalla pública de sala: sin nombre ni documento completo. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BoardEntryDTO {
    private String ticket;
    private Integer position;
    private TriageLevel triageLevel;
    private CareStatus status;
    private String destinationArea;
    private String destinationCode;
    private Integer maxWaitMinutes;
    private Integer waitedMinutes;
    private Integer estimatedWaitMinutes;
    private Boolean overdue;
    private Boolean present;
    private OffsetDateTime arrivedAt;
}
