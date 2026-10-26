package com.hospital.triage.queue.application.port;

import com.hospital.triage.common.dto.RankedPatientDTO;
import com.hospital.triage.common.enums.CareStatus;

import java.util.List;
import java.util.UUID;

public interface EvaluationPort {
    List<RankedPatientDTO> listByStatus(CareStatus status);

    List<RankedPatientDTO> listAll();

    RankedPatientDTO updateStatus(UUID patientId, CareStatus status);

    void applyPositions(List<RankedPatientDTO> ranked);
}
