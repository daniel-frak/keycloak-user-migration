ARG KEYCLOAK_IMAGE

FROM $KEYCLOAK_IMAGE

USER root

COPY . /project
#RUN cd /project && ./mvnw clean package

FROM $KEYCLOAK_IMAGE
USER root
COPY --from=0 /project/target/*.jar /opt/jboss/keycloak/standalone/deployments/app.jar
USER 1000
