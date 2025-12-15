# syntax=docker/dockerfile:1
FROM gradle:8.10-jdk17 AS builder

WORKDIR /app

# Copy only build metadata first for better layer caching
COPY build.gradle settings.gradle gradlew ./
COPY gradle ./gradle
RUN chmod +x gradlew

# Download dependencies with cache mount (cached separately from source changes)
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon dependencies

# Copy the source last to avoid invalidating dependency caches unnecessarily
COPY src ./src
COPY .env.example ./

# Build the Spring Boot fat jar with cache mount; tests are skipped for faster container builds
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon bootJar -x test \
    && BOOT_JAR=$(find build/libs -maxdepth 1 -type f -name "*.jar" ! -name "*-plain.jar" -print -quit) \
    && test -n "$BOOT_JAR" \
    && cp "$BOOT_JAR" /app/app.jar

# --- Runtime image ---
FROM eclipse-temurin:17-jre-jammy

ENV APP_HOME=/app \
    SPRING_PROFILES_ACTIVE=prod \
    JAVA_OPTS=""

WORKDIR $APP_HOME

COPY --from=builder /app/app.jar ./app.jar

# Render provides $PORT; default to 8080 when running locally
EXPOSE 8080

CMD ["sh", "-c", "java $JAVA_OPTS -Dserver.port=${PORT:-8080} -jar app.jar"]
