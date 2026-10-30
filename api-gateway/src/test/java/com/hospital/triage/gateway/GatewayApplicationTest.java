package com.hospital.triage.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayApplicationTest {

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("GW-01: El contexto del API Gateway debe iniciar correctamente")
    void contextLoads() {
        assertThat(context).isNotNull();
    }
}

