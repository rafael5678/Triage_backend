package com.hospital.triage.application.service;

import com.hospital.triage.application.dto.CreateUserRequest;
import com.hospital.triage.application.dto.LoginRequest;
import com.hospital.triage.application.dto.SessionUserDTO;
import com.hospital.triage.domain.entity.AppUser;
import com.hospital.triage.domain.exception.CredencialesInvalidasException;
import com.hospital.triage.infrastructure.persistence.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AppUserRepository users;
    private final PasswordEncoder encoder;

    public SessionUserDTO login(LoginRequest request) {
        AppUser user = users.findByUsernameIgnoreCase(request.getUsername().trim())
                .orElseThrow(CredencialesInvalidasException::new);
        if (!user.isActive() || !encoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new CredencialesInvalidasException();
        }
        return toSession(user);
    }

    @Transactional
    public SessionUserDTO createUser(CreateUserRequest request) {
        if (users.existsByUsernameIgnoreCase(request.getUsername().trim())) {
            throw new IllegalArgumentException("Ese usuario ya existe");
        }
        AppUser user = new AppUser();
        user.setUsername(request.getUsername().trim());
        user.setPasswordHash(encoder.encode(request.getPassword()));
        user.setDisplayName(request.getDisplayName().trim());
        user.setRole(request.getRole());
        user.setActive(true);
        user.setCreatedAt(OffsetDateTime.now());
        return toSession(users.save(user));
    }

    public List<SessionUserDTO> listUsers() {
        return users.findAll().stream().map(this::toSession).toList();
    }

    @Transactional
    public void setActive(java.util.UUID id, boolean active) {
        AppUser user = users.findById(id).orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
        user.setActive(active);
        users.save(user);
    }

    private SessionUserDTO toSession(AppUser user) {
        return SessionUserDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .displayName(user.getDisplayName())
                .role(user.getRole())
                .active(user.isActive())
                .build();
    }
}
