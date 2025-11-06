#!/bin/bash
set -e

# Load .env if present
if [ -f .env ]; then
  export $(grep -v '^#' .env | xargs)
fi

# Validate required env vars
: "${EXECUTOR_CLASS:?EXECUTOR_CLASS must be set in .env}"

echo "Running executor: $EXECUTOR_CLASS"

# DEBUG: Check if Maven wrapper exists and is executable
echo "=== Debug Information ==="
echo "Current directory: $(pwd)"
echo "APP_HOME: ${APP_HOME:-Not set}"
echo "Maven wrapper check:"
if [ -f "/app/mvnw" ]; then
    echo "✓ Maven wrapper found at /app/mvnw"
    ls -la /app/mvnw
    echo "Testing Maven wrapper:"
    /app/mvnw --version
else
    echo "✗ ERROR: Maven wrapper NOT found at /app/mvnw"
    echo "Directory contents of /app:"
    ls -la /app/
    exit 1
fi

# Ensure APP_HOME is set
if [ -z "$APP_HOME" ]; then
  export APP_HOME=/app
  echo "APP_HOME not set, defaulting to: $APP_HOME"
fi

echo "=== Starting Application ==="
# Validate required env vars
: "${EXECUTOR_CLASS:?EXECUTOR_CLASS must be set in .env}"

# Use absolute path to mvnw
exec /app/mvnw exec:java \
  -Dexec.mainClass="$EXECUTOR_CLASS" \
  -Dsystems.properties.path=/app/target/classes/systems.properties