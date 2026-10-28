package com.hospital.triage.auth.infrastructure.grpc;

import io.grpc.BindableService;
import io.grpc.servlet.jakarta.GrpcServlet;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Cloud Run expone un solo puerto, así que gRPC comparte el de REST.
 * Requiere Jetty con server.http2.enabled (h2c): Tomcat pierde los errores gRPC sin cuerpo.
 */
@Configuration
public class GrpcServletConfig {

    @Bean
    public ServletRegistrationBean<GrpcServlet> grpcServlet(List<BindableService> services) {
        String[] paths = services.stream()
                .map(service -> "/" + service.bindService().getServiceDescriptor().getName() + "/*")
                .toArray(String[]::new);
        ServletRegistrationBean<GrpcServlet> registration =
                new ServletRegistrationBean<>(new GrpcServlet(services), paths);
        registration.setName("grpc");
        registration.setAsyncSupported(true);
        registration.setLoadOnStartup(1);
        return registration;
    }
}
