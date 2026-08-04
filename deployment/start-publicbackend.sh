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

# The deployment runs under the `production` profile. It is activated here rather than left at
# the defaults so that deployment-only behaviour can be gated positively — @Profile("production"),
# <springProfile name="production"> — instead of by a negation list that would have to name every
# development profile forever. `local` and `travel` are development profiles, `test` is used by the
# integration tests.
export SPRING_PROFILES_ACTIVE=production

# Overridable from publicbackend.env
JAVA_OPTS="${JAVA_OPTS:--Xmx2g}"

mkdir -p "$FOLDER_BASE/logs"

echo "Starting publicbackend..."
echo "  deployment dir : $FOLDER_BASE"
echo "  profile        : $SPRING_PROFILES_ACTIVE"
echo "  java opts      : $JAVA_OPTS"
echo "  log            : $LOG_FILE"

# Writing to $LOG_FILE is logback's job (see logback-spring.xml and logging.file.name in
# application-production.properties), NOT tee's. Piping through tee would make stdout a pipe
# rather than a terminal, and the terminal dashboard needs a real TTY to detect a usable screen.
#
# exec replaces this shell with the JVM so that Ctrl-C reaches the JVM directly — the dashboard
# installs a SIGINT handler to restore the terminal before exiting.
# shellcheck disable=SC2086
exec java $JAVA_OPTS -jar publicbackend.jar
