FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /src
COPY pom.xml .
COPY triage-common-contracts ./triage-common-contracts
COPY triage-grpc-contracts ./triage-grpc-contracts
COPY ms-pacientes-auth ./ms-pacientes-auth
COPY ms-evaluacion-triaje ./ms-evaluacion-triaje
COPY ms-cola-espera ./ms-cola-espera
COPY ms-ia-recomendacion ./ms-ia-recomendacion
COPY legacy-monolith ./legacy-monolith
RUN mvn -q -DskipTests -pl ms-pacientes-auth,ms-evaluacion-triaje,ms-cola-espera,ms-ia-recomendacion -am package \
    && mkdir -p /jars \
    && cp ms-pacientes-auth/target/ms-pacientes-auth-0.1.0.jar /jars/ \
    && cp ms-evaluacion-triaje/target/ms-evaluacion-triaje-0.1.0.jar /jars/ \
    && cp ms-cola-espera/target/ms-cola-espera-0.1.0.jar /jars/ \
    && cp ms-ia-recomendacion/target/ms-ia-recomendacion-0.1.0.jar /jars/

FROM eclipse-temurin:17-jre
LABEL org.opencontainers.image.source=https://github.com/rafael5678/Triage_backend
WORKDIR /app
COPY --from=build /jars /app
ENV MODULE=ms-pacientes-auth
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java -XX:MaxRAMPercentage=75 -XX:TieredStopAtLevel=1 -Djava.net.preferIPv4Stack=true -jar /app/${MODULE}-0.1.0.jar"]
