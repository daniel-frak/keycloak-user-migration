ARG MAVEN_IMAGE
ARG OPENJDK_IMAGE
FROM $MAVEN_IMAGE AS build
COPY src /usr/legacy-system-example/src
COPY pom.xml /usr/legacy-system-example
RUN mvn -f /usr/legacy-system-example/pom.xml clean package

FROM $OPENJDK_IMAGE
VOLUME /tmp
ARG JAR_FILE=target/*.jar
COPY --from=build /usr/legacy-system-example/${JAR_FILE} app.jar
ENTRYPOINT ["java","-jar","/app.jar"]