# ==============================================================================
# Dockerfile para TriageIA - API Gateway
# Puerto: 8080 | Multi-stage build para optimización de imagen en Azure
# ==============================================================================

# Etapa 1: Compilación
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /src
COPY pom.xml .
COPY triage-common-contracts ./triage-common-contracts
COPY api-gateway ./api-gateway
RUN mvn -B -q -pl api-gateway -am clean package -DskipTests

# Etapa 2: Runtime ligero
FROM eclipse-temurin:17-jre-alpine
LABEL maintainer="Equipo TriageIA"
LABEL org.opencontainers.image.title="triage-api-gateway"
LABEL org.opencontainers.image.description="API Gateway y router perimetral para TriageIA"

WORKDIR /app
COPY --from=build /src/api-gateway/target/api-gateway-0.1.0.jar app.jar

ENV PORT=8080
EXPOSE 8080

RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+UseG1GC", "-Djava.net.preferIPv4Stack=true", "-jar", "app.jar"]

