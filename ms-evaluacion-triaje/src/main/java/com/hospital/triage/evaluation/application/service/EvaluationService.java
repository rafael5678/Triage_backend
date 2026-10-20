package com.hospital.triage.evaluation.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.triage.common.dto.AiAssistDTO;
import com.hospital.triage.common.dto.AnalyzeTriageDTO;
import com.hospital.triage.common.dto.RankedPatientDTO;
import com.hospital.triage.common.dto.ReassessmentDTO;
import com.hospital.triage.common.dto.VitalSignsDTO;
import com.hospital.triage.common.enums.CareStatus;
import com.hospital.triage.common.exception.PatientNotFoundException;
import com.hospital.triage.evaluation.domain.entity.Patient;
import com.hospital.triage.evaluation.domain.entity.TriageEvaluation;
import com.hospital.triage.evaluation.domain.entity.VitalSigns;
import com.hospital.triage.evaluation.infrastructure.persistence.PatientRepository;
import com.hospital.triage.evaluation.infrastructure.persistence.TriageEvaluationRepository;
import com.hospital.triage.evaluation.infrastructure.persistence.VitalSignsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class EvaluationService {

    private final TriageService triageService;
    private final GeminiEvaluationClient geminiClient;
    private final PatientRepository patients;
    private final VitalSignsRepository vitalSignsRepo;
    private final TriageEvaluationRepository evaluations;
    private final ObjectMapper json;

    public RankedPatientDTO evaluate(VitalSignsDTO dto) {
        Patient p = patients.findById(dto.getPatientId())
                .orElseThrow(() -> new PatientNotFoundException(dto.getPatientId().toString()));

        VitalSigns s = new VitalSigns();
        s.setPatient(p);
        s.setHeartRate(dto.getHeartRate());
        s.setSpo2(dto.getSpo2());
        s.setSystolic(dto.getSystolic());
        s.setDiastolic(dto.getDiastolic());
        s.setTemperature(dto.getTemperature());
        s.setRespiratoryRate(dto.getRespiratoryRate());
        s.setRecordedAt(OffsetDateTime.now());
        vitalSignsRepo.save(s);

        var protocol = triageService.calculate(
                p.getAge(), dto.getHeartRate(), dto.getSpo2(),
                dto.getSystolic(), dto.getDiastolic(),
                dto.getTemperature(), dto.getRespiratoryRate(),
                p.getConditionId(), p.getHistory()
        );

        AiAssistDTO ia = geminiClient.assist(
                p.getConditionId(), p.getReason(), p.getHistory(), p.getAge(), p.getSex(),
                dto.getHeartRate(), dto.getSpo2(), dto.getSystolic(), dto.getDiastolic(),
                dto.getTemperature(), dto.getRespiratoryRate(),
                protocol.score().doubleValue(), protocol.news2(), null
        );

        if (ia.getProtocolScore() == null) {
            ia.setProtocolScore(protocol.score().doubleValue());
        }

        var r = triageService.merge(protocol, ia);

        TriageEvaluation ev = evaluations.findTopByPatient_IdOrderByEvaluatedAtDesc(p.getId())
                .filter(e -> e.getStatus() == CareStatus.EN_ESPERA)
                .orElseGet(TriageEvaluation::new);
        ev.setPatient(p);
        ev.setVitalSigns(s);
        ev.setRiskScore(r.score());
        ev.setTriageLevel(r.level());
        ev.setStatus(CareStatus.EN_ESPERA);
        ev.setNews2(r.news2());
        ev.setEvaluatedAt(OffsetDateTime.now());
        ev.setScoreSource(ia.isActive() ? "HYBRID" : "PROTOCOL");
        ev.setAiExplanation(serializeAi(ia));
        evaluations.save(ev);

        return toDto(p, ev, s, ev.getQueuePosition());
    }

    public RankedPatientDTO reassess(UUID patientId, ReassessmentDTO dto) {
        VitalSignsDTO vitals = new VitalSignsDTO();
        vitals.setPatientId(patientId);
        vitals.setHeartRate(dto.getHeartRate());
        vitals.setSpo2(dto.getSpo2());
        vitals.setSystolic(dto.getSystolic());
        vitals.setDiastolic(dto.getDiastolic());
        vitals.setTemperature(dto.getTemperature());
        vitals.setRespiratoryRate(dto.getRespiratoryRate());
        return evaluate(vitals);
    }

    @Transactional(readOnly = true)
    public RankedPatientDTO analyze(AnalyzeTriageDTO dto) {
        var protocol = triageService.calculate(
                dto.getAge(), dto.getHeartRate(), dto.getSpo2(),
                dto.getSystolic(), dto.getDiastolic(),
                dto.getTemperature(), dto.getRespiratoryRate(),
                dto.getConditionId(), dto.getHistory()
        );

        AiAssistDTO ia = geminiClient.assist(
                dto.getConditionId(), dto.getReason(), dto.getHistory(), dto.getAge(), dto.getSex(),
                dto.getHeartRate(), dto.getSpo2(), dto.getSystolic(), dto.getDiastolic(),
                dto.getTemperature(), dto.getRespiratoryRate(),
                protocol.score().doubleValue(), protocol.news2(), dto.getLocalModelScore()
        );

        if (ia.getProtocolScore() == null) {
            ia.setProtocolScore(protocol.score().doubleValue());
        }

        var r = triageService.merge(protocol, ia);

        Patient fake = new Patient();
        fake.setName(dto.getName());
        fake.setDocument(dto.getDocument());
        fake.setAge(dto.getAge());
        fake.setSex(dto.getSex());
        fake.setConditionId(dto.getConditionId());
        fake.setReason(dto.getReason());
        fake.setHistory(dto.getHistory());

        VitalSigns s = new VitalSigns();
        s.setHeartRate(dto.getHeartRate());
        s.setSpo2(dto.getSpo2());
        s.setSystolic(dto.getSystolic());
        s.setDiastolic(dto.getDiastolic());
        s.setTemperature(dto.getTemperature());
        s.setRespiratoryRate(dto.getRespiratoryRate());

        TriageEvaluation ev = new TriageEvaluation();
        ev.setRiskScore(r.score());
        ev.setTriageLevel(r.level());
        ev.setNews2(r.news2());
        ev.setEvaluatedAt(OffsetDateTime.now());
        ev.setScoreSource(ia.isActive() ? "HYBRID" : "PROTOCOL");
        ev.setAiExplanation(serializeAi(ia));
        ev.setStatus(CareStatus.EN_ESPERA);

        return toDto(fake, ev, s, null);
    }

    public boolean isGeminiConfigured() {
        return geminiClient.configured();
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

    private String serializeAi(AiAssistDTO ia) {
        try {
            return json.writeValueAsString(ia);
        } catch (Exception e) {
            return null;
        }
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
