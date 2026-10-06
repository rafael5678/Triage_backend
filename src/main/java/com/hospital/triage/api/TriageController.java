package com.hospital.triage.api;

import com.hospital.triage.application.dto.EstadoColaTriageDTO;
import com.hospital.triage.application.dto.IngresoPacienteDTO;
import com.hospital.triage.application.dto.PacientePriorizadoDTO;
import com.hospital.triage.application.dto.ReevaluacionDTO;
import com.hospital.triage.application.dto.RegistroSignosVitalesDTO;
import com.hospital.triage.application.service.ColaAtencionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/triage")
@RequiredArgsConstructor
public class TriageController {

    private final ColaAtencionService cola;

    @GetMapping("/salud")
    public Map<String, String> salud() {
        return Map.of("status", "ok", "servicio", "hospital-triaje-api");
    }

    @GetMapping("/pacientes")
    public List<PacientePriorizadoDTO> pacientes() {
        return cola.todos();
    }

    @PostMapping("/pacientes")
    @ResponseStatus(HttpStatus.CREATED)
    public PacientePriorizadoDTO registrar(@Valid @RequestBody IngresoPacienteDTO dto) {
        return cola.registrarPaciente(dto);
    }

    @PostMapping("/evaluaciones")
    public PacientePriorizadoDTO evaluar(@Valid @RequestBody RegistroSignosVitalesDTO dto) {
        return cola.evaluar(dto);
    }

    @PutMapping("/evaluaciones/{pacienteId}/reevaluar")
    public PacientePriorizadoDTO reevaluar(@PathVariable UUID pacienteId, @Valid @RequestBody ReevaluacionDTO dto) {
        return cola.reevaluar(pacienteId, dto);
    }

    @GetMapping("/cola")
    public EstadoColaTriageDTO cola() {
        return cola.cola();
    }

    @PatchMapping("/atender/{pacienteId}")
    public PacientePriorizadoDTO atender(@PathVariable UUID pacienteId) {
        return cola.atender(pacienteId);
    }
}
