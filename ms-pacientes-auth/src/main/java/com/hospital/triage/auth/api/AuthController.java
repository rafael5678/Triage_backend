package com.hospital.triage.auth.api;

import com.hospital.triage.auth.application.service.AuthService;
import com.hospital.triage.common.dto.CreateUserRequest;
import com.hospital.triage.common.dto.LoginRequest;
import com.hospital.triage.common.dto.SessionUserDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService auth;

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok", "service", "ms-pacientes-auth");
    }

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

    @PatchMapping({"/admin/users/{id}/active", "/admin/users/{id}/activo"})
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setActive(@PathVariable UUID id,
                          @RequestParam(value = "active", required = false) Boolean active,
                          @RequestParam(value = "activo", required = false) Boolean activo) {
        boolean value = active != null ? active : Boolean.TRUE.equals(activo);
        auth.setActive(id, value);
    }
}
