FROM eclipse-temurin:21-jdk-jammy AS build

WORKDIR /workspace

COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY src src

RUN chmod +x mvnw \
    && ./mvnw -B -DskipTests package

FROM eclipse-temurin:21-jre-jammy

RUN apt-get update \
    && apt-get upgrade --no-install-recommends --yes \
    && apt-get install --no-install-recommends --yes curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 --create-home --home-dir /home/docflow docflow

WORKDIR /app

COPY --from=build --chown=docflow:docflow \
    /workspace/target/dockflow-0.0.1-SNAPSHOT.jar /app/app.jar

USER docflow

EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=5s --start-period=45s --retries=6 \
    CMD curl --fail --silent http://127.0.0.1:8080/actuator/health/liveness > /dev/null || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
