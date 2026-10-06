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
        cfg.setConnectionTimeout(30_000);
        cfg.addDataSourceProperty("tcpKeepAlive", "true");
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
        String host = uri.getHost();
        int port = uri.getPort() == -1 ? 5432 : uri.getPort();
        boolean pooler = host != null && host.contains("pooler.supabase.com");
        boolean remoto = host != null && !host.equals("localhost") && !host.equals("127.0.0.1") && !host.equals("db");
        String extra = remoto ? "?sslmode=require" : "";
        if (pooler || port == 6543) {
            extra = extra.isEmpty() ? "?prepareThreshold=0" : extra + "&prepareThreshold=0";
        }
        cfg.setJdbcUrl("jdbc:postgresql://" + host + ":" + port + uri.getPath() + extra);
        cfg.setUsername(user);
        cfg.setPassword(pass);
    }
}
