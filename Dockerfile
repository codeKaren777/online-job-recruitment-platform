# syntax=docker/dockerfile:1.7

FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw

# Resolve dependencies separately from application source changes.
COPY src/ src/

# Reuse Maven's cached dependencies for compilation and tests.
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -ntp \
      -Dmaven.wagon.http.retryHandler.count=5 \
      package

FROM eclipse-temurin:21-jre-jammy
RUN groupadd --system recruitment && \
    useradd --system --gid recruitment recruitment
WORKDIR /app

COPY --from=build --chown=recruitment:recruitment \
    /workspace/target/recruitment.jar app.jar

USER recruitment
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
