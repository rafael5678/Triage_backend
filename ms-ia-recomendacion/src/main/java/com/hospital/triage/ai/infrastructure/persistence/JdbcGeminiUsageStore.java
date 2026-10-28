package com.hospital.triage.ai.infrastructure.persistence;

import com.hospital.triage.ai.application.service.GeminiUsageStore;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;

/** Suma atómica en Postgres: dos réplicas que llamen a la vez no se saltan el tope. */
@Component
@RequiredArgsConstructor
public class JdbcGeminiUsageStore implements GeminiUsageStore {

    private final JdbcTemplate jdbc;

    @Override
    public int incrementAndGet(LocalDate day) {
        Integer total = jdbc.queryForObject("""
                INSERT INTO uso_gemini (dia, llamadas) VALUES (?, 1)
                ON CONFLICT (dia) DO UPDATE SET llamadas = uso_gemini.llamadas + 1
                RETURNING llamadas
                """, Integer.class, Date.valueOf(day));
        return total == null ? 0 : total;
    }

    @Override
    public int get(LocalDate day) {
        List<Integer> rows = jdbc.queryForList("SELECT llamadas FROM uso_gemini WHERE dia = ?", Integer.class, Date.valueOf(day));
        return rows.isEmpty() ? 0 : rows.get(0);
    }
}
