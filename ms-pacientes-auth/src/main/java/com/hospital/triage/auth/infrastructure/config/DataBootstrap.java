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
    private final PasswordVault vault;

    @Value("${app.bootstrap.admin-username:}")
    private String adminUsername;

    @Value("${app.bootstrap.admin-password:}")
    private String adminPassword;

    @Override
    public void run(String... args) {
        if (adminUsername != null && !adminUsername.isBlank() && adminPassword != null && !adminPassword.isBlank()) {
            AppUser admin = users.findByUsernameIgnoreCase(adminUsername.trim()).orElseGet(AppUser::new);
            boolean created = admin.getId() == null;
            if (created) {
                admin.setUsername(adminUsername.trim());
                admin.setPasswordHash(encoder.encode(adminPassword));
                admin.setPasswordVault(vault.seal(adminPassword));
                admin.setDisplayName("Administrador");
                admin.setRole(UserRole.ADMIN);
                admin.setActive(true);
                admin.setCreatedAt(OffsetDateTime.now());
                users.save(admin);
                log.info("Admin account ready: {}", adminUsername);
            } else if (admin.getPasswordVault() == null || admin.getPasswordVault().isBlank()) {
                if (encoder.matches(adminPassword, admin.getPasswordHash())) {
                    admin.setPasswordVault(vault.seal(adminPassword));
                    users.save(admin);
                    log.info("Admin password vault filled for {}", adminUsername);
                }
            }
        }
    }
}
