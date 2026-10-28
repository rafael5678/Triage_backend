package com.hospital.triage.auth.infrastructure.grpc;

import com.hospital.triage.grpc.v1.PatientId;
import com.hospital.triage.grpc.v1.PatientLookupGrpc;
import com.hospital.triage.grpc.v1.PatientView;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(
        classes = GrpcOverHttpPortTest.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "server.http2.enabled=true"
)
class GrpcOverHttpPortTest {

    @LocalServerPort
    private int port;

    private ManagedChannel channel;

    @AfterEach
    void close() throws InterruptedException {
        if (channel != null) {
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    @DisplayName("GR-01: Una llamada gRPC en h2c llega por el mismo puerto que REST")
    void grpcCallSharesRestPort() {
        channel = NettyChannelBuilder.forAddress("localhost", port).usePlaintext().build();

        PatientView view = PatientLookupGrpc.newBlockingStub(channel)
                .withDeadlineAfter(5, TimeUnit.SECONDS)
                .getPatient(PatientId.newBuilder().setId("7f1c2a4e-0000-4000-8000-000000000001").build());

        assertThat(view.getName()).isEqualTo("Paciente gRPC");
    }

    @Test
    @DisplayName("GR-02: El estado de error gRPC viaja en los trailers")
    void grpcErrorStatusTravels() {
        channel = NettyChannelBuilder.forAddress("localhost", port).usePlaintext().build();

        assertThatThrownBy(() -> PatientLookupGrpc.newBlockingStub(channel)
                .withDeadlineAfter(5, TimeUnit.SECONDS)
                .getPatient(PatientId.newBuilder().setId("no-existe").build()))
                .isInstanceOf(StatusRuntimeException.class)
                .satisfies(ex -> assertThat(((StatusRuntimeException) ex).getStatus().getCode())
                        .as(((StatusRuntimeException) ex).getStatus().toString())
                        .isEqualTo(Status.Code.NOT_FOUND));
    }

    @Test
    @DisplayName("GR-03: REST sigue respondiendo en HTTP/1.1 con el servlet gRPC registrado")
    void restStillAnswers() {
        String body = new TestRestTemplate().getForObject("http://localhost:" + port + "/api/v1/ping", String.class);
        assertThat(body).isEqualTo("pong");
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class})
    @Import({GrpcServletConfig.class, PingController.class})
    static class TestApp {

        @Bean
        PatientLookupGrpc.PatientLookupImplBase fakeLookup() {
            return new PatientLookupGrpc.PatientLookupImplBase() {
                @Override
                public void getPatient(PatientId request, StreamObserver<PatientView> observer) {
                    if (!request.getId().startsWith("7f1c")) {
                        observer.onError(Status.NOT_FOUND.withDescription("sin paciente").asRuntimeException());
                        return;
                    }
                    observer.onNext(PatientView.newBuilder().setId(request.getId()).setName("Paciente gRPC").build());
                    observer.onCompleted();
                }
            };
        }
    }

    @RestController
    static class PingController {
        @GetMapping("/api/v1/ping")
        String ping() {
            return "pong";
        }
    }
}
