package com.hospital.triage.infrastructure.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.net.URI;

/**
 * Render no alcanza el host IPv6 db.*.supabase.co.
 * Si llega esa URI, se reescribe al Session pooler (IPv4).
 */
@Configuration
public class DatasourceConfig {

    private static final Logger log = LoggerFactory.getLogger(DatasourceConfig.class);
    private static final String POOLER = "aws-0-us-west-2.pooler.supabase.com";

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
            aplicarUrl(cfg, databaseUrl);
        } else {
            cfg.setJdbcUrl(jdbcUrl);
            cfg.setUsername(username);
            cfg.setPassword(password);
        }
        return new HikariDataSource(cfg);
    }

    private void aplicarUrl(HikariConfig cfg, String databaseUrl) {
        String normalized = databaseUrl.replace("postgres://", "postgresql://");
        if (normalized.startsWith("jdbc:")) {
            normalized = normalized.substring("jdbc:".length());
        }
        URI uri = URI.create(normalized);
        String userInfo = uri.getUserInfo();
        String user = userInfo.split(":", 2)[0];
        String pass = userInfo.split(":", 2).length > 1 ? userInfo.split(":", 2)[1] : "";
        String host = uri.getHost();
        int port = uri.getPort() == -1 ? 5432 : uri.getPort();

        if (host != null && host.startsWith("db.") && host.endsWith(".supabase.co")) {
            String ref = host.substring("db.".length(), host.length() - ".supabase.co".length());
            host = POOLER;
            port = 6543;
            if (!user.contains(".")) {
                user = user + "." + ref;
            }
            log.info("Supabase directo reescrito a pooler IPv4 {} @ {}:{}", user, host, port);
        }

        boolean pooler = host != null && host.contains("pooler.supabase.com");
        boolean remoto = host != null && !host.equals("localhost") && !host.equals("127.0.0.1") && !host.equals("db");
        String extra = remoto ? "?sslmode=require" : "";
        if (pooler || port == 6543) {
            extra = extra.isEmpty() ? "?prepareThreshold=0" : extra + "&prepareThreshold=0";
        }
        cfg.setJdbcUrl("jdbc:postgresql://" + host + ":" + port + uri.getPath() + extra);
        cfg.setUsername(user);
        cfg.setPassword(pass);
        log.info("JDBC host={} port={} user={}", host, port, user);
    }
}
