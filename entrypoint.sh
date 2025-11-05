#!/bin/sh
set -e

# Load .env if present (docker-compose usually injects envs, but this helps for docker run)
if [ -f .env ]; then
  export $(grep -v '^#' .env | xargs)
fi

: "${EXECUTOR_CLASS:?EXECUTOR_CLASS must be set in .env}"

echo "Running executor: $EXECUTOR_CLASS"

# Classpath includes the thin jar and the raw compiled classes
exec java -cp /app/app.jar \
  -Dconfig.file=/app/target/classes/systems.properties \
  "$EXECUTOR_CLASS"

