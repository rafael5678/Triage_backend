package com.hospital.triage.api;

import com.hospital.triage.application.dto.CreateUserRequest;
import com.hospital.triage.application.dto.LoginRequest;
import com.hospital.triage.application.dto.SessionUserDTO;
import com.hospital.triage.application.dto.SymptomRequest;
import com.hospital.triage.application.service.AuthService;
import com.hospital.triage.application.service.CatalogService;
import com.hospital.triage.domain.entity.Symptom;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AuthAdminController {

    private final AuthService auth;
    private final CatalogService catalog;

    @PostMapping("/auth/login")
    public SessionUserDTO login(@Valid @RequestBody LoginRequest request) {
        return auth.login(request);
    }

    @GetMapping("/admin/users")
    public List<SessionUserDTO> users() {
        return auth.listUsers();
    }

    @PostMapping("/admin/users")
    @ResponseStatus(HttpStatus.CREATED)
    public SessionUserDTO createUser(@Valid @RequestBody CreateUserRequest request) {
        return auth.createUser(request);
    }

    @PatchMapping("/admin/users/{id}/activo")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setActive(@PathVariable UUID id, @RequestParam boolean activo) {
        auth.setActive(id, activo);
    }

    @GetMapping("/catalogo/sintomas")
    public List<Symptom> activeSymptoms() {
        return catalog.listActive();
    }

    @GetMapping("/admin/sintomas")
    public List<Symptom> allSymptoms() {
        return catalog.listAll();
    }

    @PostMapping("/admin/sintomas")
    @ResponseStatus(HttpStatus.CREATED)
    public Symptom saveSymptom(@Valid @RequestBody SymptomRequest request) {
        return catalog.save(request);
    }

    @DeleteMapping("/admin/sintomas/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSymptom(@PathVariable String id) {
        catalog.delete(id);
    }
}
