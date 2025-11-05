# ---- Build stage ----
FROM eclipse-temurin:22-jdk AS build
WORKDIR /app

# Copy Maven wrapper and pom.xml first (to leverage caching)
COPY mvnw* pom.xml ./
COPY .mvn .mvn
COPY lib ./lib

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
RUN ./mvnw -B clean package -DskipTests

# Debug: list build outputs to confirm paths inside the container
RUN ls -l /app/target && \
    echo "Contents of /app/target/classes:" && \
    ls -R /app/target/classes || true

# ---- Runtime stage ----
FROM eclipse-temurin:22-jre
WORKDIR /app

# Copy compiled classes (since you're mounting systems.properties into /app/target/classes)
COPY --from=build /app/target/classes /app/target/classes

# Copy the thin jar explicitly (adjust version if it changes)
COPY --from=build /app/target/atscale-gatling-core-1.6.jar /app/app.jar

# Entry point
COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

# Runtime mounts
VOLUME ["/app/run_logs", "/app/app_logs", "/app/queries"]

ENTRYPOINT ["/entrypoint.sh"]
