# syntax=docker/dockerfile:1
# Multi-stage: compila el jar DENTRO de Docker (no requiere Maven/JDK ni jar pre-construido).
# El build context es ./services (el reactor) para ver el pom padre; compila solo este modulo.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY . .
RUN --mount=type=cache,target=/root/.m2 mvn -q -pl catalog-service package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /build/catalog-service/target/catalog-service-1.0.0.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java","-jar","app.jar"]
