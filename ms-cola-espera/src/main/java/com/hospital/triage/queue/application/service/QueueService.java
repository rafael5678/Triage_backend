package com.hospital.triage.queue.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.triage.common.dto.AiAssistDTO;
import com.hospital.triage.common.dto.QueueStateDTO;
import com.hospital.triage.common.dto.RankedPatientDTO;
import com.hospital.triage.common.enums.CareStatus;
import com.hospital.triage.common.exception.PatientNotFoundException;
import com.hospital.triage.queue.domain.entity.Patient;
import com.hospital.triage.queue.domain.entity.TriageEvaluation;
import com.hospital.triage.queue.domain.entity.VitalSigns;
import com.hospital.triage.queue.infrastructure.persistence.PatientRepository;
import com.hospital.triage.queue.infrastructure.persistence.TriageEvaluationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class QueueService {

    private final PatientRepository patients;
    private final TriageEvaluationRepository evaluations;
    private final ObjectMapper json;

    @Transactional(readOnly = true)
    public QueueStateDTO queue() {
        List<RankedPatientDTO> list = ranked(CareStatus.EN_ESPERA);
        return QueueStateDTO.builder()
                .generatedAt(OffsetDateTime.now())
                .waitingCount(list.size())
                .queue(list)
                .build();
    }

    @Transactional(readOnly = true)
    public List<RankedPatientDTO> all() {
        return latestByPatient(evaluations.findAllByOrderByEvaluatedAtDesc());
    }

    public RankedPatientDTO startCare(UUID patientId) {
        return updateStatus(patientId, CareStatus.EN_ATENCION);
    }

    public RankedPatientDTO updateStatus(UUID patientId, CareStatus newStatus) {
        Patient p = patients.findById(patientId)
                .orElseThrow(() -> new PatientNotFoundException(patientId.toString()));
        TriageEvaluation ev = evaluations.findTopByPatient_IdOrderByEvaluatedAtDesc(patientId)
                .orElseThrow(() -> new PatientNotFoundException(patientId.toString()));

        ev.setStatus(newStatus);
        if (newStatus != CareStatus.EN_ESPERA) {
            ev.setQueuePosition(null);
        }
        evaluations.save(ev);
        reorder();
        return toDto(p, ev, ev.getVitalSigns(), ev.getQueuePosition());
    }

    public void reorder() {
        List<RankedPatientDTO> ranked = ranked(CareStatus.EN_ESPERA);
        for (RankedPatientDTO dto : ranked) {
            evaluations.findTopByPatient_IdOrderByEvaluatedAtDesc(dto.getPatientId()).ifPresent(ev -> {
                ev.setQueuePosition(dto.getQueuePosition());
                evaluations.save(ev);
            });
        }
    }

    public List<RankedPatientDTO> ranked(CareStatus status) {
        List<TriageEvaluation> raw = evaluations.findByStatusOrderByRiskScoreDescEvaluatedAtAsc(status);
        List<RankedPatientDTO> latest = latestByPatient(raw);
        latest.sort(Comparator
                .comparing(RankedPatientDTO::getRiskScore, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(RankedPatientDTO::getEvaluatedAt));
        int i = 1;
        for (RankedPatientDTO p : latest) {
            p.setQueuePosition(i++);
        }
        return latest;
    }

    private List<RankedPatientDTO> latestByPatient(List<TriageEvaluation> raw) {
        Map<UUID, RankedPatientDTO> map = new LinkedHashMap<>();
        for (TriageEvaluation ev : raw) {
            UUID id = ev.getPatient().getId();
            map.putIfAbsent(id, toDto(ev.getPatient(), ev, ev.getVitalSigns(), ev.getQueuePosition()));
        }
        return new ArrayList<>(map.values());
    }

    public RankedPatientDTO toDto(Patient p, TriageEvaluation ev, VitalSigns s, Integer pos) {
        RankedPatientDTO.RankedPatientDTOBuilder b = RankedPatientDTO.builder()
                .patientId(p.getId())
                .name(p.getName())
                .document(p.getDocument())
                .age(p.getAge())
                .sex(p.getSex())
                .conditionId(p.getConditionId())
                .reason(p.getReason())
                .history(p.getHistory())
                .queuePosition(pos);

        if (ev != null) {
            AiAssistDTO ia = readAi(ev.getAiExplanation());
            b.riskScore(ev.getRiskScore())
                    .triageLevel(ev.getTriageLevel())
                    .status(ev.getStatus())
                    .news2(ev.getNews2())
                    .evaluatedAt(ev.getEvaluatedAt())
                    .scoreSource(ev.getScoreSource())
                    .aiActive(ia.isActive())
                    .dangerLead(ia.getDangerLead())
                    .aiMeaning(ia.getMeaning())
                    .protocolScore(ia.getProtocolScore())
                    .geminiScore(ia.getSuggestedScore())
                    .aiReasons(ia.getReasons() == null ? List.of() : ia.getReasons())
                    .datasetReasons(ia.getDatasetReasons() == null ? List.of() : ia.getDatasetReasons())
                    .geminiReasons(ia.getGeminiReasons() == null ? List.of() : ia.getGeminiReasons());
        }

        if (s != null) {
            b.heartRate(s.getHeartRate())
                    .spo2(s.getSpo2())
                    .systolic(s.getSystolic())
                    .diastolic(s.getDiastolic())
                    .temperature(s.getTemperature())
                    .respiratoryRate(s.getRespiratoryRate());
        }
        return b.build();
    }

    private AiAssistDTO readAi(String raw) {
        if (raw == null || raw.isBlank()) {
            return AiAssistDTO.builder().active(false).build();
        }
        try {
            return json.readValue(raw, AiAssistDTO.class);
        } catch (Exception e) {
            return AiAssistDTO.builder().active(false).build();
        }
    }
}
