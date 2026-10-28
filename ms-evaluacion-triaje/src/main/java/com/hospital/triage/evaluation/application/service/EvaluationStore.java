package com.hospital.triage.evaluation.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.triage.common.dto.AiAssistDTO;
import com.hospital.triage.common.dto.RankedPatientDTO;
import com.hospital.triage.common.dto.VitalSignsDTO;
import com.hospital.triage.common.enums.CareStatus;
import com.hospital.triage.common.exception.PatientNotFoundException;
import com.hospital.triage.evaluation.application.port.PatientSnapshot;
import com.hospital.triage.evaluation.domain.entity.TriageEvaluation;
import com.hospital.triage.evaluation.domain.entity.VitalSigns;
import com.hospital.triage.evaluation.infrastructure.persistence.TriageEvaluationRepository;
import com.hospital.triage.evaluation.infrastructure.persistence.VitalSignsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class EvaluationStore {

    private final VitalSignsRepository vitalSignsRepo;
    private final TriageEvaluationRepository evaluations;
    private final ObjectMapper json;

    public RankedPatientDTO save(PatientSnapshot patient, VitalSignsDTO dto, TriageService.RiskResult result, AiAssistDTO ia,
                                 DestinationService.Destination destination) {
        VitalSigns signs = new VitalSigns();
        signs.setPatientId(patient.id());
        signs.setHeartRate(dto.getHeartRate());
        signs.setSpo2(dto.getSpo2());
        signs.setSystolic(dto.getSystolic());
        signs.setDiastolic(dto.getDiastolic());
        signs.setTemperature(dto.getTemperature());
        signs.setRespiratoryRate(dto.getRespiratoryRate());
        signs.setGlucose(dto.getGlucose());
        signs.setPainScale(dto.getPainScale());
        signs.setConsciousness(TriageService.avpu(dto.getConsciousness()));
        signs.setRecordedAt(OffsetDateTime.now());
        vitalSignsRepo.save(signs);

        TriageEvaluation evaluation = evaluations.findTopByPatientIdOrderByEvaluatedAtDesc(patient.id())
                .filter(existing -> existing.getStatus() == CareStatus.EN_ESPERA)
                .orElseGet(TriageEvaluation::new);
        evaluation.setPatientId(patient.id());
        evaluation.setVitalSigns(signs);
        evaluation.setRiskScore(result.score());
        evaluation.setTriageLevel(result.level());
        evaluation.setStatus(CareStatus.EN_ESPERA);
        evaluation.setNews2(result.news2());
        evaluation.setEvaluatedAt(OffsetDateTime.now());
        evaluation.setScoreSource(ia.isActive() ? "HYBRID" : "PROTOCOL");
        evaluation.setAiExplanation(RankedPatientAssembler.serializeAi(ia, json));
        evaluation.setDestinationArea(destination.area().label());
        evaluation.setDestinationCode(destination.code());
        evaluation.setDestinationReason(truncate(destination.reason(), 300));
        evaluation.setMaxWaitMinutes(destination.maxWaitMinutes());
        evaluation.setEstimatedFields(RankedPatientAssembler.joinFields(dto.getEstimatedFields()));
        evaluations.save(evaluation);
        return RankedPatientAssembler.toDto(patient, evaluation, signs, evaluation.getQueuePosition(), json);
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    @Transactional(readOnly = true)
    public List<RankedPatientDTO> loadByStatus(CareStatus status) {
        return toClinical(unique(evaluations.findByStatusOrderByRiskScoreDescEvaluatedAtAsc(status)));
    }

    @Transactional(readOnly = true)
    public List<RankedPatientDTO> loadAll() {
        return toClinical(unique(evaluations.findAllByOrderByEvaluatedAtDesc()));
    }

    public RankedPatientDTO updateStatus(UUID patientId, CareStatus newStatus) {
        TriageEvaluation evaluation = evaluations.findTopByPatientIdOrderByEvaluatedAtDesc(patientId)
                .orElseThrow(() -> new PatientNotFoundException(patientId.toString()));
        evaluation.setStatus(newStatus);
        if (newStatus != CareStatus.EN_ESPERA) {
            evaluation.setQueuePosition(null);
        }
        evaluations.save(evaluation);
        VitalSigns signs = evaluation.getVitalSigns();
        if (signs != null) {
            signs.getHeartRate();
        }
        return RankedPatientAssembler.toDto(PatientSnapshot.unknown(patientId), evaluation, signs, evaluation.getQueuePosition(), json);
    }

    public void applyPositions(Map<UUID, Integer> positions) {
        for (Map.Entry<UUID, Integer> entry : positions.entrySet()) {
            evaluations.findTopByPatientIdOrderByEvaluatedAtDesc(entry.getKey()).ifPresent(evaluation -> {
                if (evaluation.getStatus() == CareStatus.EN_ESPERA) {
                    evaluation.setQueuePosition(entry.getValue());
                    evaluations.save(evaluation);
                }
            });
        }
    }

    private List<RankedPatientDTO> toClinical(List<TriageEvaluation> rows) {
        List<RankedPatientDTO> out = new ArrayList<>();
        for (TriageEvaluation evaluation : rows) {
            VitalSigns signs = evaluation.getVitalSigns();
            if (signs != null) {
                signs.getHeartRate();
            }
            out.add(RankedPatientAssembler.toDto(
                    PatientSnapshot.unknown(evaluation.getPatientId()),
                    evaluation,
                    signs,
                    evaluation.getQueuePosition(),
                    json));
        }
        return out;
    }

    private List<TriageEvaluation> unique(List<TriageEvaluation> raw) {
        Map<UUID, TriageEvaluation> map = new LinkedHashMap<>();
        for (TriageEvaluation evaluation : raw) {
            map.putIfAbsent(evaluation.getPatientId(), evaluation);
        }
        return new ArrayList<>(map.values());
    }
}
