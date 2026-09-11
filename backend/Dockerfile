# syntax=docker/dockerfile:1.7

FROM maven:3.9.16-eclipse-temurin-21 AS builder
WORKDIR /workspace

COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -Dmaven.test.skip=true dependency:go-offline

COPY src/main/ src/main/
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -Dmaven.test.skip=true clean package && \
    cp target/backend-*.jar /workspace/app.jar

COPY docker/HealthProbe.java /workspace/health-probe/HealthProbe.java
RUN javac -d /workspace/health-probe /workspace/health-probe/HealthProbe.java && \
    rm /workspace/health-probe/HealthProbe.java

FROM eclipse-temurin:21-jre-jammy AS runtime

RUN groupadd --gid 10001 tayyar && \
    useradd --uid 10001 --gid tayyar --no-create-home --home-dir /nonexistent \
        --shell /usr/sbin/nologin tayyar && \
    install -d -o root -g tayyar -m 0750 /opt/tayyar /opt/tayyar/health-probe

WORKDIR /opt/tayyar
COPY --from=builder --chown=root:tayyar --chmod=0440 /workspace/app.jar ./app.jar
COPY --from=builder --chown=root:tayyar --chmod=0440 /workspace/health-probe/HealthProbe.class \
    ./health-probe/HealthProbe.class

ENV SPRING_PROFILES_ACTIVE=production
EXPOSE 8080
USER 10001:10001

ENTRYPOINT ["java", "-jar", "/opt/tayyar/app.jar"]
