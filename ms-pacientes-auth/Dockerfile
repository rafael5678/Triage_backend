# ==============================================================================
# Dockerfile para TriageIA - ms-pacientes-auth
# Puerto: 8081 | Multi-stage build para optimización de imagen en Azure
# ==============================================================================

# Etapa 1: Compilación
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /src
COPY pom.xml .
COPY triage-common-contracts ./triage-common-contracts
COPY triage-grpc-contracts ./triage-grpc-contracts
COPY ms-pacientes-auth ./ms-pacientes-auth
RUN mvn -B -q -pl ms-pacientes-auth -am clean package -DskipTests

# Etapa 2: Runtime ligero
FROM eclipse-temurin:17-jre-alpine
LABEL maintainer="Equipo TriageIA"
LABEL org.opencontainers.image.title="triage-ms-pacientes-auth"
LABEL org.opencontainers.image.description="Microservicio de Gestión de Pacientes y Autenticación"

WORKDIR /app
COPY --from=build /src/ms-pacientes-auth/target/ms-pacientes-auth-0.1.0.jar app.jar

ENV PORT=8081
EXPOSE 8081

RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+UseG1GC", "-Djava.net.preferIPv4Stack=true", "-jar", "app.jar"]

