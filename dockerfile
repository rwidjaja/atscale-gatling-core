# ---- Build stage ----
FROM eclipse-temurin:22-jdk AS build
WORKDIR /app

ENV APP_HOME=/app

# Copy Maven wrapper and pom.xml first (to leverage caching)
COPY mvnw* pom.xml ./
COPY .mvn .mvn
COPY lib ./lib

RUN apt-get update && apt-get install -y maven

# Generate Maven wrapper
RUN mvn wrapper:wrapper

RUN chmod +x mvnw

# Install Hive JDBC into local repo before dependency resolution
RUN ./mvnw install:install-file \
  -Dfile=lib/hive-jdbc-uber-2.6.3.0-235.jar \
  -DgroupId=veil.hdp.hive \
  -DartifactId=hive-jdbc-uber \
  -Dversion=2.6.3.0-235 \
  -Dpackaging=jar

# Pre-fetch dependencies
RUN ./mvnw -B dependency:go-offline

# Copy source code
COPY src ./src

# Build the project
RUN ./mvnw -B clean package -DskipTests -Denforcer.skip=true

# Debug: list build outputs
RUN find /app/target -name "*.jar" -type f

# ---- Runtime stage ----
FROM eclipse-temurin:22-jdk
WORKDIR /app

# Copy the application JAR
COPY --from=build /app/target/atscale-gatling-core-*.jar /app/app.jar

# Copy ALL Maven wrapper files for runtime - FIXED: Use absolute paths
COPY --from=build /app/mvnw /app/mvnw
COPY --from=build /app/mvnw.cmd /app/mvnw.cmd
COPY --from=build /app/pom.xml /app/pom.xml
COPY --from=build /app/.mvn /app/.mvn

# Ensure Maven wrapper is executable
RUN chmod +x /app/mvnw

# Copy lib directory for Hive JDBC (needed for MavenCentral.java)
COPY --from=build /app/lib /app/lib

# Re-install Hive JDBC in runtime image (for MavenCentral.java)
RUN /app/mvnw install:install-file \
  -Dfile=lib/hive-jdbc-uber-2.6.3.0-235.jar \
  -DgroupId=veil.hdp.hive \
  -DartifactId=hive-jdbc-uber \
  -Dversion=2.6.3.0-235 \
  -Dpackaging=jar

# Verify Maven wrapper is working in runtime - USE ABSOLUTE PATH
RUN echo "=== Maven Wrapper Check ===" && \
    ls -la /app/mvnw && \
    /app/mvnw --version

# Create directory structure for mounted volumes
RUN mkdir -p /app/run_logs /app/app_logs /app/queries /app/target/classes

# Debug: Show what files are available
RUN echo "=== Runtime files ===" && \
    ls -la /app/ && \
    echo "=== Maven wrapper files ===" && \
    ls -la /app/.mvn/wrapper/ && \
    echo "=== Lib files ===" && \
    ls -la /app/lib/

# Entry point
COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

# Runtime mounts
VOLUME ["/app/run_logs", "/app/app_logs", "/app/queries", "/app/target/classes"]

ENTRYPOINT ["/entrypoint.sh"]