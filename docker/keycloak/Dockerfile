ARG KEYCLOAK_IMAGE
ARG MAVEN_IMAGE

FROM $MAVEN_IMAGE as builder
COPY pom.xml /tmp/
COPY src /tmp/src
WORKDIR /tmp/
RUN mvn clean package

FROM $KEYCLOAK_IMAGE
USER root
COPY --from=builder /tmp/target/*.jar /opt/keycloak/providers/app.jar
USER 1000

ENTRYPOINT ["/opt/keycloak/bin/kc.sh", "start-dev"]