package com.hospital.triage.infrastructure.config;

import com.hospital.triage.domain.entity.AppUser;
import com.hospital.triage.domain.entity.Symptom;
import com.hospital.triage.domain.enums.UserRole;
import com.hospital.triage.infrastructure.persistence.AppUserRepository;
import com.hospital.triage.infrastructure.persistence.SymptomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataBootstrap implements CommandLineRunner {

    private final AppUserRepository users;
    private final SymptomRepository symptoms;
    private final PasswordEncoder encoder;

    @Value("${app.bootstrap.admin-username:}")
    private String adminUsername;

    @Value("${app.bootstrap.admin-password:}")
    private String adminPassword;

    @Override
    public void run(String... args) {
        if (users.count() == 0 && !adminUsername.isBlank() && !adminPassword.isBlank()) {
            AppUser admin = new AppUser();
            admin.setUsername(adminUsername.trim());
            admin.setPasswordHash(encoder.encode(adminPassword));
            admin.setDisplayName("Administrador");
            admin.setRole(UserRole.ADMIN);
            admin.setActive(true);
            admin.setCreatedAt(OffsetDateTime.now());
            users.save(admin);
            log.info("Cuenta administrador inicial creada: {}", adminUsername);
        }
        if (symptoms.count() == 0) {
            List.of(
                    row("paro", "Se desmayó o no responde", "No habla, no se mueve o no respira bien.", 99),
                    row("avc", "Cara, brazo o habla de pronto rara", "Boca torcida o un lado débil.", 94),
                    row("sca", "Dolor fuerte en el pecho", "Presión en el pecho, a veces al brazo.", 88),
                    row("trauma", "Golpe o accidente fuerte", "Caída, choque o herida grave.", 90),
                    row("disnea", "Le cuesta mucho respirar", "Ahogo o no puede terminar una frase.", 82),
                    row("sepsis", "Infección que lo ve muy mal", "Fiebre con palidez o confusión.", 80),
                    row("anafilaxia", "Alergia grave", "Hinchazón de cara o dificultad para respirar.", 86),
                    row("hemorragia", "Sangrado que no para", "Sangre que sigue saliendo.", 84),
                    row("convulsion", "Convulsión", "Temblor de todo el cuerpo hace poco.", 70),
                    row("abdomen", "Dolor fuerte de panza", "Dolor de abdomen que no es leve.", 52),
                    row("quemadura", "Quemadura", "Fuego, líquido caliente o químico.", 48),
                    row("fractura", "Hueso lastimado", "Dolor al mover o no puede apoyar.", 38),
                    row("cefalea", "Dolor de cabeza", "Sin otros signos graves.", 26),
                    row("fiebre", "Fiebre, pero se ve estable", "Calentura sin ahogo.", 24),
                    row("gi", "Diarrea, vómito o malestar", "Síntomas digestivos leves.", 18),
                    row("cura", "Herida pequeña o curación", "Corte menor o vendaje.", 8)
            ).forEach(symptoms::save);
            log.info("Catálogo inicial de síntomas cargado");
        }
    }

    private Symptom row(String id, String label, String hint, int severity) {
        Symptom s = new Symptom();
        s.setId(id);
        s.setLabel(label);
        s.setHint(hint);
        s.setSeverity(severity);
        s.setActive(true);
        return s;
    }
}
