package com.hospital.triage.auth.application.service;

import com.hospital.triage.auth.domain.entity.AppUser;
import com.hospital.triage.auth.infrastructure.persistence.AppUserRepository;
import com.hospital.triage.common.dto.CreateUserRequest;
import com.hospital.triage.common.dto.LoginRequest;
import com.hospital.triage.common.dto.SessionUserDTO;
import com.hospital.triage.common.dto.UpdateUserRequest;
import com.hospital.triage.common.exception.InvalidCredentialsException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AppUserRepository users;
    private final PasswordEncoder encoder;

    public SessionUserDTO login(LoginRequest request) {
        AppUser user = users.findByUsernameIgnoreCase(request.getUsername().trim())
                .orElseThrow(InvalidCredentialsException::new);
        if (!user.isActive() || !encoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        return toSession(user);
    }

    @Transactional
    public SessionUserDTO createUser(CreateUserRequest request) {
        if (users.existsByUsernameIgnoreCase(request.getUsername().trim())) {
            throw new IllegalArgumentException("That username already exists");
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
    public SessionUserDTO updateUser(UUID id, UpdateUserRequest request) {
        AppUser user = users.findById(id).orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (request.getUsername() != null && !request.getUsername().isBlank()) {
            String username = request.getUsername().trim();
            if (username.contains(" ") || username.length() < 3 || username.length() > 32) {
                throw new IllegalArgumentException("Username must be 3 to 32 characters without spaces");
            }
            users.findByUsernameIgnoreCase(username).ifPresent(other -> {
                if (!other.getId().equals(id)) {
                    throw new IllegalArgumentException("That username already exists");
                }
            });
            user.setUsername(username);
        }
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            String password = request.getPassword();
            if (password.contains(" ") || password.length() < 8 || password.length() > 64) {
                throw new IllegalArgumentException("Password must be 8 to 64 characters without spaces");
            }
            user.setPasswordHash(encoder.encode(password));
        }
        if (request.getDisplayName() != null && !request.getDisplayName().isBlank()) {
            user.setDisplayName(request.getDisplayName().trim());
        }
        if (request.getRole() != null) {
            user.setRole(request.getRole());
        }
        return toSession(users.save(user));
    }

    @Transactional
    public void setActive(UUID id, boolean active) {
        AppUser user = users.findById(id).orElseThrow(() -> new IllegalArgumentException("User not found"));
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
