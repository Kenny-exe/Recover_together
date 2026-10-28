# RecoverTogether backend image.
# Build:  docker build -t recovertogether-backend .
# Run:    docker compose up -d --build   (see docker-compose.yml)
#
# Multi-stage: a Maven/Temurin-21 builder produces the Spring Boot fat jar, a
# slim Temurin-21 JRE runtime runs it as a non-root user. The fat jar is kept
# plain on purpose (no layered extraction) — keep it simple until profiling
# proves image layer optimization is worth the maintenance.

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Prime the dependency cache first so day-to-day source changes do not
# re-download the whole dependency tree.
COPY mvnw ./
COPY .mvn ./.mvn
COPY pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src ./src
RUN ./mvnw -B -q package -DskipTests

FROM eclipse-temurin:21-jre AS runtime
RUN useradd --system --no-create-home --uid 1001 appuser
WORKDIR /app
COPY --from=build /workspace/target/backend-0.0.1-SNAPSHOT.jar app.jar
USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
