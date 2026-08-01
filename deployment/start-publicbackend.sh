#!/bin/bash

# Starts publicbackend in the foreground. Invoked from deployment/bin by `just start`.
#
# Secrets are NOT in the repo: they live in <deployment-dir>/publicbackend.env on the deploy machine
# only. This script sources that file, which exports the same variable names Render used, so the
# ${DB_URL}-style placeholders in application.properties resolve unchanged.

set -euo pipefail

# Check if argument is provided
if [ $# -eq 0 ]; then
    echo "Error: deployment base path is required"
    echo "Usage: $0 <deployment-dir>"
    echo "Example: $0 /Users/jesper/develop/production/publicbackend/deployment"
    exit 1
fi

export FOLDER_BASE="$1"
ENV_FILE="$FOLDER_BASE/publicbackend.env"
LOG_FILE="$FOLDER_BASE/logs/publicbackend.log"

if [ ! -f "$ENV_FILE" ]; then
    echo "Error: $ENV_FILE not found."
    echo "Copy deployment/publicbackend.env.example to that path and fill in the real values."
    exit 1
fi

# shellcheck disable=SC1090
source "$ENV_FILE"

# No profile is activated: the deployment runs on the defaults in application.properties.
# `local` and `travel` are the only profiles, and both are development-only.

# Overridable from publicbackend.env
JAVA_OPTS="${JAVA_OPTS:--Xmx2g}"

mkdir -p "$FOLDER_BASE/logs"

echo "Starting publicbackend..."
echo "  deployment dir : $FOLDER_BASE"
echo "  profile        : (default)"
echo "  java opts      : $JAVA_OPTS"
echo "  log            : $LOG_FILE"

# shellcheck disable=SC2086
java $JAVA_OPTS -jar publicbackend.jar 2>&1 | tee -a "$LOG_FILE"
