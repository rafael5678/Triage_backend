# ==============================================================================
# Dockerfile para TriageIA - ms-evaluacion-triaje
# Puerto: 8082 | Multi-stage build para optimización de imagen en Azure
# ==============================================================================

# Etapa 1: Compilación
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /src
COPY pom.xml .
COPY triage-common-contracts ./triage-common-contracts
COPY triage-grpc-contracts ./triage-grpc-contracts
COPY ms-evaluacion-triaje ./ms-evaluacion-triaje
RUN mvn -B -q -pl ms-evaluacion-triaje -am clean package -DskipTests

# Etapa 2: Runtime ligero
FROM eclipse-temurin:17-jre-alpine
LABEL maintainer="Equipo TriageIA"
LABEL org.opencontainers.image.title="triage-ms-evaluacion-triaje"
LABEL org.opencontainers.image.description="Microservicio de Evaluación Clínica y Signos Vitales"

WORKDIR /app
COPY --from=build /src/ms-evaluacion-triaje/target/ms-evaluacion-triaje-0.1.0.jar app.jar

ENV PORT=8082
EXPOSE 8082

RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+UseG1GC", "-Djava.net.preferIPv4Stack=true", "-jar", "app.jar"]

