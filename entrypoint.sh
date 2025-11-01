#!/bin/bash
set -e

# Load .env if present
if [ -f .env ]; then
  export $(grep -v '^#' .env | xargs)
fi

# Validate required env vars
: "${EXECUTOR_CLASS:?EXECUTOR_CLASS must be set in .env}"

echo "Running executor: $EXECUTOR_CLASS"

exec ./mvnw exec:java \
  -Dexec.mainClass="$EXECUTOR_CLASS" \
  -Dsystems.properties.path=/app/systems.properties
