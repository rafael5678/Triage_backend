package com.hospital.triage.infrastructure.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.net.URI;

/**
 * Acepta DATABASE_URL de Render (postgres://...) o DB_URL local (jdbc:postgresql://...).
 */
@Configuration
public class DatasourceConfig {

    @Bean
    @Primary
    public DataSource dataSource(
            @Value("${DATABASE_URL:}") String databaseUrl,
            @Value("${spring.datasource.url}") String jdbcUrl,
            @Value("${spring.datasource.username}") String username,
            @Value("${spring.datasource.password}") String password
    ) {
        HikariConfig cfg = new HikariConfig();
        cfg.setDriverClassName("org.postgresql.Driver");
        cfg.setMaximumPoolSize(5);
        if (databaseUrl != null && !databaseUrl.isBlank()) {
            aplicarRender(cfg, databaseUrl);
        } else {
            cfg.setJdbcUrl(jdbcUrl);
            cfg.setUsername(username);
            cfg.setPassword(password);
        }
        return new HikariDataSource(cfg);
    }

    private void aplicarRender(HikariConfig cfg, String databaseUrl) {
        String normalized = databaseUrl.replace("postgres://", "postgresql://");
        if (normalized.startsWith("jdbc:")) {
            cfg.setJdbcUrl(normalized);
            return;
        }
        URI uri = URI.create(normalized);
        String userInfo = uri.getUserInfo();
        String user = userInfo.split(":", 2)[0];
        String pass = userInfo.split(":", 2).length > 1 ? userInfo.split(":", 2)[1] : "";
        int port = uri.getPort() == -1 ? 5432 : uri.getPort();
        String ssl = uri.getHost() != null && uri.getHost().contains("render.com") ? "?sslmode=require" : "";
        cfg.setJdbcUrl("jdbc:postgresql://" + uri.getHost() + ":" + port + uri.getPath() + ssl);
        cfg.setUsername(user);
        cfg.setPassword(pass);
    }
}
