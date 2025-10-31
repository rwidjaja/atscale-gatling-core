# ---- Build Stage ----
FROM eclipse-temurin:19-jdk AS builder
WORKDIR /app

# Install Maven
RUN apt-get update && apt-get install -y maven && rm -rf /var/lib/apt/lists/*

# Copy project files
COPY . .

# Make Maven wrapper executable
RUN chmod +x mvnw

# Install Hive JDBC dependency
RUN if [ -f ./lib/hive-jdbc-uber-2.6.3.0-235.jar ]; then \
      ./mvnw install:install-file \
        -Dfile=./lib/hive-jdbc-uber-2.6.3.0-235.jar \
        -DgroupId=veil.hdp.hive \
        -DartifactId=hive-jdbc-uber \
        -Dversion=2.6.3.0-235 \
        -Dpackaging=jar; \
    else \
      echo "Hive JDBC JAR not found, skipping installation"; \
    fi

# Build the project
RUN ./mvnw clean package -DskipTests -Dgpg.skip=true -Denforcer.skip=true

# Copy dependencies
RUN ./mvnw dependency:copy-dependencies -DoutputDirectory=target/dependency

# ---- Runtime Stage ----
FROM eclipse-temurin:19-jre-jammy
WORKDIR /app

# Copy built artifacts
COPY --from=builder /app/target/classes /app/target/classes
COPY --from=builder /app/target/dependency /app/target/dependency

# Create necessary directories
RUN mkdir -p /app/config /app/output /app/run_logs

# Don't copy the example file - we'll use the mounted one from ./config/systems.properties
# COPY --from=builder /app/example_systems.properties /app/config/systems.properties

# Environment variables (will be overridden by .env)
ENV ATSCALE_QUERY_OUTPUT_DIR=/app/run_logs

# Run the executor - uses EXECUTOR_CLASS from .env file
CMD ["sh", "-c", "\
  java -cp 'target/classes:target/dependency/*' \
  -Dconfig.file=/app/config/systems.properties \
  -Datscale.username=${ATSCALE_USERNAME} \
  -Datscale.password=${ATSCALE_PASSWORD} \
  -Datscale.url=${ATSCALE_URL} \
  -Datscale.project='${ATSCALE_PROJECT}' \
  -Datscale.schema='${ATSCALE_SCHEMA}' \
  -Datscale.query.output.dir=${ATSCALE_QUERY_OUTPUT_DIR} \
  ${EXECUTOR_CLASS}"]