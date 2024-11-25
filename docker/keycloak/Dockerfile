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
# JVM options for remote debugging:
ENV JAVA_OPTS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"
USER 1000

ENTRYPOINT ["/opt/keycloak/bin/kc.sh", "start-dev"]