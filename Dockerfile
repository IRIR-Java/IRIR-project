# ---- Build Stage ----
FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /app

# Copy Maven wrapper and pom first (cache dependency layer)
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Fix line endings and permissions in case of CRLF (Windows checkout)
RUN sed -i 's/\r//' mvnw && chmod +x mvnw

# Download dependencies (cached unless pom.xml changes)
RUN ./mvnw dependency:go-offline -B -q

# Copy source and build
COPY src/ src/
RUN ./mvnw package -B -DskipTests -q

# ---- Runtime Stage ----
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Create directory for persistent data (Railway Volume mount point)
RUN mkdir -p /data/uploads /data/lucene-index

# Default to persistent volume paths (overridden by Railway env vars if needed)
ENV UPLOAD_DIR=/data/uploads
ENV LUCENE_INDEX_DIR=/data/lucene-index

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

# Railway injects PORT env var — honour it so the healthcheck can reach the app
ENTRYPOINT ["sh", "-c", "java -jar app.jar --server.port=${PORT:-8080} --spring.profiles.active=${SPRING_PROFILES_ACTIVE:-railway}"]
