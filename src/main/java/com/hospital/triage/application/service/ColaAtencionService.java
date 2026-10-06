package com.hospital.triage.application.service;

import com.hospital.triage.application.dto.EstadoColaTriageDTO;
import com.hospital.triage.application.dto.IngresoPacienteDTO;
import com.hospital.triage.application.dto.PacientePriorizadoDTO;
import com.hospital.triage.application.dto.ReevaluacionDTO;
import com.hospital.triage.application.dto.RegistroSignosVitalesDTO;
import com.hospital.triage.domain.entity.EvaluacionTriage;
import com.hospital.triage.domain.entity.Paciente;
import com.hospital.triage.domain.entity.SignosVitales;
import com.hospital.triage.domain.enums.EstadoAtencion;
import com.hospital.triage.domain.exception.PacienteNoEncontradoException;
import com.hospital.triage.infrastructure.persistence.EvaluacionTriageRepository;
import com.hospital.triage.infrastructure.persistence.PacienteRepository;
import com.hospital.triage.infrastructure.persistence.SignosVitalesRepository;
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
public class ColaAtencionService {

    private final TriageService triageService;
    private final PacienteRepository pacientes;
    private final SignosVitalesRepository signosRepo;
    private final EvaluacionTriageRepository evaluaciones;

    public PacientePriorizadoDTO registrarPaciente(IngresoPacienteDTO dto) {
        Paciente p = new Paciente();
        p.setNombre(dto.getNombre());
        p.setDocumento(dto.getDocumento());
        p.setEdad(dto.getEdad());
        p.setSexo(dto.getSexo());
        p.setLesionId(dto.getLesionId());
        p.setMotivo(dto.getMotivo());
        p.setAntecedentes(dto.getAntecedentes());
        p.setCreadoEn(OffsetDateTime.now());
        return toDto(pacientes.save(p), null, null, null);
    }

    public PacientePriorizadoDTO evaluar(RegistroSignosVitalesDTO dto) {
        Paciente p = pacientes.findById(dto.getPacienteId())
                .orElseThrow(() -> new PacienteNoEncontradoException(dto.getPacienteId().toString()));
        SignosVitales s = new SignosVitales();
        s.setPaciente(p);
        s.setFrecuenciaCardiaca(dto.getFrecuenciaCardiaca());
        s.setSpo2(dto.getSpo2());
        s.setPresionSistolica(dto.getPresionSistolica());
        s.setPresionDiastolica(dto.getPresionDiastolica());
        s.setTemperatura(dto.getTemperatura());
        s.setFrecuenciaRespiratoria(dto.getFrecuenciaRespiratoria());
        s.setRegistradoEn(OffsetDateTime.now());
        signosRepo.save(s);

        var r = triageService.calcular(
                p.getEdad(), dto.getFrecuenciaCardiaca(), dto.getSpo2(),
                dto.getPresionSistolica(), dto.getPresionDiastolica(),
                dto.getTemperatura(), dto.getFrecuenciaRespiratoria(),
                p.getLesionId(), p.getAntecedentes()
        );

        EvaluacionTriage ev = evaluaciones.findTopByPaciente_IdOrderByEvaluadoEnDesc(p.getId())
                .filter(e -> e.getEstado() == EstadoAtencion.EN_ESPERA)
                .orElseGet(EvaluacionTriage::new);
        ev.setPaciente(p);
        ev.setSignos(s);
        ev.setScoreRiesgo(r.score());
        ev.setNivelTriage(r.nivel());
        ev.setEstado(EstadoAtencion.EN_ESPERA);
        ev.setNews2(r.news2());
        ev.setEvaluadoEn(OffsetDateTime.now());
        evaluaciones.save(ev);
        reordenar();
        return cola().getCola().stream()
                .filter(x -> x.getPacienteId().equals(p.getId()))
                .findFirst()
                .orElse(toDto(p, ev, s, ev.getPosicionCola()));
    }

    public PacientePriorizadoDTO reevaluar(UUID pacienteId, ReevaluacionDTO dto) {
        RegistroSignosVitalesDTO signos = new RegistroSignosVitalesDTO();
        signos.setPacienteId(pacienteId);
        signos.setFrecuenciaCardiaca(dto.getFrecuenciaCardiaca());
        signos.setSpo2(dto.getSpo2());
        signos.setPresionSistolica(dto.getPresionSistolica());
        signos.setPresionDiastolica(dto.getPresionDiastolica());
        signos.setTemperatura(dto.getTemperatura());
        signos.setFrecuenciaRespiratoria(dto.getFrecuenciaRespiratoria());
        return evaluar(signos);
    }

    public PacientePriorizadoDTO atender(UUID pacienteId) {
        Paciente p = pacientes.findById(pacienteId)
                .orElseThrow(() -> new PacienteNoEncontradoException(pacienteId.toString()));
        EvaluacionTriage ev = evaluaciones.findTopByPaciente_IdOrderByEvaluadoEnDesc(pacienteId)
                .orElseThrow(() -> new PacienteNoEncontradoException(pacienteId.toString()));
        ev.setEstado(EstadoAtencion.EN_ATENCION);
        ev.setPosicionCola(null);
        evaluaciones.save(ev);
        reordenar();
        return toDto(p, ev, ev.getSignos(), null);
    }

    @Transactional(readOnly = true)
    public EstadoColaTriageDTO cola() {
        List<PacientePriorizadoDTO> lista = ranked(EstadoAtencion.EN_ESPERA);
        return EstadoColaTriageDTO.builder()
                .generadoEn(OffsetDateTime.now())
                .totalEnEspera(lista.size())
                .cola(lista)
                .build();
    }

    @Transactional(readOnly = true)
    public List<PacientePriorizadoDTO> todos() {
        return latestByPatient(evaluaciones.findAllByOrderByEvaluadoEnDesc());
    }

    private void reordenar() {
        List<PacientePriorizadoDTO> ranked = ranked(EstadoAtencion.EN_ESPERA);
        for (PacientePriorizadoDTO dto : ranked) {
            evaluaciones.findTopByPaciente_IdOrderByEvaluadoEnDesc(dto.getPacienteId()).ifPresent(ev -> {
                ev.setPosicionCola(dto.getPosicionCola());
                evaluaciones.save(ev);
            });
        }
    }

    private List<PacientePriorizadoDTO> ranked(EstadoAtencion estado) {
        List<EvaluacionTriage> raw = evaluaciones.findByEstadoOrderByScoreRiesgoDescEvaluadoEnAsc(estado);
        List<PacientePriorizadoDTO> latest = latestByPatient(raw);
        latest.sort(Comparator
                .comparing(PacientePriorizadoDTO::getScoreRiesgo, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(PacientePriorizadoDTO::getEvaluadoEn));
        int i = 1;
        for (PacientePriorizadoDTO p : latest) {
            p.setPosicionCola(i++);
        }
        return latest;
    }

    private List<PacientePriorizadoDTO> latestByPatient(List<EvaluacionTriage> raw) {
        Map<UUID, PacientePriorizadoDTO> map = new LinkedHashMap<>();
        for (EvaluacionTriage ev : raw) {
            UUID id = ev.getPaciente().getId();
            map.putIfAbsent(id, toDto(ev.getPaciente(), ev, ev.getSignos(), ev.getPosicionCola()));
        }
        return new ArrayList<>(map.values());
    }

    private PacientePriorizadoDTO toDto(Paciente p, EvaluacionTriage ev, SignosVitales s, Integer pos) {
        PacientePriorizadoDTO.PacientePriorizadoDTOBuilder b = PacientePriorizadoDTO.builder()
                .pacienteId(p.getId())
                .nombre(p.getNombre())
                .documento(p.getDocumento())
                .edad(p.getEdad())
                .sexo(p.getSexo())
                .lesionId(p.getLesionId())
                .motivo(p.getMotivo())
                .antecedentes(p.getAntecedentes())
                .posicionCola(pos);
        if (ev != null) {
            b.scoreRiesgo(ev.getScoreRiesgo())
                    .nivelTriage(ev.getNivelTriage())
                    .estado(ev.getEstado())
                    .news2(ev.getNews2())
                    .evaluadoEn(ev.getEvaluadoEn());
        }
        if (s != null) {
            b.frecuenciaCardiaca(s.getFrecuenciaCardiaca())
                    .spo2(s.getSpo2())
                    .presionSistolica(s.getPresionSistolica())
                    .presionDiastolica(s.getPresionDiastolica())
                    .temperatura(s.getTemperatura())
                    .frecuenciaRespiratoria(s.getFrecuenciaRespiratoria());
        }
        return b.build();
    }
}
