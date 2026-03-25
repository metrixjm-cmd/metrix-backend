# Stage 1: Build
FROM maven:3.9.5-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -q
COPY src/ ./src/
RUN mvn package -DskipTests -q

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
# Cloud Run inyecta PORT=8080; Spring Boot lo respeta vía SERVER_PORT
ENV SERVER_PORT=8080
ENTRYPOINT ["java", "-jar", "app.jar"]
