FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw
COPY src/ src/
RUN ./mvnw -B -ntp package

FROM eclipse-temurin:21-jre-jammy
RUN groupadd --system recruitment && useradd --system --gid recruitment recruitment
WORKDIR /app
COPY --from=build --chown=recruitment:recruitment /workspace/target/recruitment.jar app.jar
USER recruitment
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
