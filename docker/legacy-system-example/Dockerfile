FROM maven:3.8-openjdk-11-slim AS build
COPY src /usr/legacy-system-example/src
COPY pom.xml /usr/legacy-system-example
RUN mvn -f /usr/legacy-system-example/pom.xml clean package

FROM openjdk:11-jdk-slim
VOLUME /tmp
ARG JAR_FILE=target/*.jar
COPY --from=build /usr/legacy-system-example/${JAR_FILE} app.jar
ENTRYPOINT ["java","-jar","/app.jar"]