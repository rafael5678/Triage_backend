package com.hospital.triage.gateway.infrastructure.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * El gateway y cada microservicio añaden CORS. El navegador rechaza un
 * Access-Control-Allow-Origin duplicado y el login sale como "Failed to fetch".
 * Nos quedamos con un solo valor, el primero.
 */
@Component
public class UniqueCorsHeaderFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        exchange.getResponse().beforeCommit(() -> {
            HttpHeaders headers = exchange.getResponse().getHeaders();
            collapseToFirstOrigin(headers, HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN);
            collapseDuplicateLines(headers, HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS);
            return Mono.empty();
        });
        return chain.filter(exchange);
    }

    /** El navegador solo acepta un origen, no "url,url". */
    private static void collapseToFirstOrigin(HttpHeaders headers, String name) {
        List<String> values = headers.get(name);
        if (values == null || values.isEmpty()) {
            return;
        }
        String first = values.get(0).split(",")[0].trim();
        headers.set(name, first);
    }

    private static void collapseDuplicateLines(HttpHeaders headers, String name) {
        List<String> values = headers.get(name);
        if (values == null || values.size() <= 1) {
            return;
        }
        headers.set(name, values.get(0).split(",")[0].trim());
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
