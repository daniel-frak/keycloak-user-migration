FROM jboss/keycloak:10.0.1

ARG JAR_FILE=target/*.jar

USER root

COPY . /project
RUN cd /project && ./mvnw clean package && mv ${JAR_FILE} /opt/jboss/keycloak/standalone/deployments/app.jar

USER 1000