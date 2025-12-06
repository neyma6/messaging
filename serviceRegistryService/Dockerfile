# Use a multi-architecture base image ensuring support for Apple Silicon (ARM64) as well as AMD64
FROM eclipse-temurin:21-jdk-jammy AS builder

WORKDIR /app

# Copy the Gradle wrapper and build configuration files from the root context
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .

# Copy the service source code
COPY serviceRegistryService serviceRegistryService

# Grant execution rights to the gradle wrapper
RUN chmod +x gradlew

# Build the application
# We use -x test to skip tests during the build process
RUN ./gradlew :serviceRegistryService:bootJar -x test

# Stage 2: Runtime
# Use a smaller JRE image for the final artifact
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# Copy the built jar from the builder stage
COPY --from=builder /app/serviceRegistryService/build/libs/*.jar app.jar

# Expose the application port (assuming 8081 for this service to avoid conflict with user-service)
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
