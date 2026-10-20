# Multi-stage Dockerfile for CareFlow Backend

# Stage 1: Build with Maven and JDK 21
FROM maven:3.9.9-eclipse-temurin-21 AS builder
WORKDIR /workspace

# Cache dependencies
COPY backend/pom.xml .
RUN mvn dependency:go-offline -B

# Copy source and build jar
COPY backend/src ./src
RUN mvn clean package -DskipTests -B

# Stage 2: Lightweight Runtime with JRE 21
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Create unprivileged application user
RUN groupadd -r careflow && useradd -r -g careflow careflow

# Copy repackaged fat jar from builder stage
COPY --from=builder /workspace/target/careflow-backend-1.0.0.jar app.jar

RUN chown -R careflow:careflow /app
USER careflow

EXPOSE 8080

ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
