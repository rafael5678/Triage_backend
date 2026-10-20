package com.hospital.triage.auth.infrastructure.config;

import com.hospital.triage.auth.domain.entity.AppUser;
import com.hospital.triage.auth.infrastructure.persistence.AppUserRepository;
import com.hospital.triage.common.enums.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataBootstrap implements CommandLineRunner {

    private final AppUserRepository users;
    private final PasswordEncoder encoder;

    @Value("${app.bootstrap.admin-username:}")
    private String adminUsername;

    @Value("${app.bootstrap.admin-password:}")
    private String adminPassword;

    @Override
    public void run(String... args) {
        if (adminUsername != null && !adminUsername.isBlank() && adminPassword != null && !adminPassword.isBlank()) {
            AppUser admin = users.findByUsernameIgnoreCase(adminUsername.trim()).orElseGet(AppUser::new);
            boolean created = admin.getId() == null;
            admin.setUsername(adminUsername.trim());
            admin.setPasswordHash(encoder.encode(adminPassword));
            admin.setDisplayName("Administrador");
            admin.setRole(UserRole.ADMIN);
            admin.setActive(true);
            if (admin.getCreatedAt() == null) {
                admin.setCreatedAt(OffsetDateTime.now());
            }
            users.save(admin);
            log.info(created ? "Admin account ready: {}" : "Admin account updated: {}", adminUsername);
        }
    }
}
