# ==========================================
# STAGE 1: Build the Spring Boot Application
# ==========================================
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app

# Copy pom.xml and download dependencies to leverage Docker layer caching
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and build the executable JAR file
COPY src ./src
RUN mvn clean package -DskipTests

# ==========================================
# STAGE 2: Lightweight Runtime Environment
# ==========================================
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copy only the compiled JAR file from the build stage
COPY --from=build /app/target/*.jar app.jar

# Expose HTTP / WebSocket port
EXPOSE 8080

# Environment variable to enable container execution
ENV JAVA_OPTS=""

# Run the Spring Boot JAR
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]