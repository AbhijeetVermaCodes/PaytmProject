# Stage 1: Build application with Maven
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /workspace

# Copy pom.xml and download dependencies
COPY pom.xml ./
RUN mvn dependency:go-offline -B || true

# Copy source code and build package
COPY src ./src
RUN mvn clean package -DskipTests -B

# Stage 2: Minimal JRE Runtime image
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Non-root user for security
RUN groupadd -r appuser && useradd -r -g appuser appuser

# Copy built artifact from builder
COPY --from=builder /workspace/target/*.jar app.jar

# Configuration defaults
ENV PORT=8080 \
    JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

EXPOSE 8080

USER appuser

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
