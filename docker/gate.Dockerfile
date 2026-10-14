# syntax=docker/dockerfile:1
# Gate Appointment SUT image: builds the React UI and the Spring Boot API from source,
# then runs the self-contained jar on a JRE as a non-root user.

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
# Dependency layer first, so code edits do not re-download the world.
COPY pom.xml .
COPY .mvn .mvn
COPY sut/gate-domain/pom.xml sut/gate-domain/
COPY sut/gate-ui/pom.xml sut/gate-ui/
COPY sut/gate-api/pom.xml sut/gate-api/
COPY harness harness
COPY suites suites
COPY sut sut
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -pl sut/gate-api -am -DskipTests package && \
    cp sut/gate-api/target/gate-api-*-exec.jar /gate.jar

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 gate
COPY --from=build /gate.jar /app/gate.jar
USER gate
EXPOSE 8080
HEALTHCHECK --interval=5s --timeout=3s --start-period=40s --retries=12 \
  CMD ["sh", "-c", "exec 3<>/dev/tcp/127.0.0.1/8080 && printf 'GET /actuator/health HTTP/1.0\\r\\n\\r\\n' >&3 && grep -q UP <&3"]
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/gate.jar"]
