FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /app

# Download dependencies first (better layer caching)
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN ./mvnw dependency:go-offline -q

# Build the application
COPY src ./src
RUN ./mvnw package -DskipTests -q

# ---

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

# Non-root user for security
RUN addgroup -S proxy && adduser -S proxy -G proxy
USER proxy

COPY --from=build /app/target/*.jar app.jar

# 502: Modbus proxy port  8080: Spring Boot Actuator
EXPOSE 502 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=15s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["java", "-Xms64m", "-Xmx128m", "-jar", "app.jar"]
