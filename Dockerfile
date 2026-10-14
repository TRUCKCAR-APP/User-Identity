# ═══════════════════════════════════════════════════════════════════
# Multi-stage Dockerfile — TruckCar User-Identity Microservice
# Stage 1: Build con Maven + JDK 21
# Stage 2: Runtime con JRE 21 slim
# ═══════════════════════════════════════════════════════════════════

# ── Stage 1: Build ──
FROM eclipse-temurin:21-jdk AS builder

WORKDIR /app

# Copiar archivos de Maven primero (aprovecha caché de capas para deps)
COPY pom.xml .
COPY mvnw .
COPY .mvn .mvn

RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Copiar código fuente y compilar
COPY src src

RUN ./mvnw clean package -DskipTests -B

# ── Stage 2: Runtime ──
FROM eclipse-temurin:21-jre

WORKDIR /app

RUN groupadd --system appuser && useradd --system --gid appuser appuser

COPY --from=builder /app/target/*.jar app.jar

RUN chown -R appuser:appuser /app
USER appuser

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
