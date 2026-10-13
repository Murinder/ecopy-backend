# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY . .
RUN --mount=type=cache,target=/root/.m2 mvn clean package -pl shared-lib,project-service -am -DskipTests -B

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/project-service/target/*.jar app.jar
EXPOSE 8002
ENTRYPOINT ["java", "-Xmx256m", "-Xms128m", "-jar", "app.jar"]
