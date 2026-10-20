#!/bin/sh

# Determine gradle wrapper jar
APP_HOME=$(cd "$(dirname "$0")" && pwd)
CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

if [ ! -f "$CLASSPATH" ]; then
  echo "gradle-wrapper.jar not found. Generating wrapper..."
  # This relies on a system gradle; on CI we use a system gradle via actions.
  if command -v gradle >/dev/null 2>&1; then
    gradle wrapper --gradle-version 8.4
    exec gradle "$@"
  else
    echo "No gradle available to generate wrapper. Install gradle or include gradle-wrapper.jar."
    exit 1
  fi
fi

export CLASSPATH
exec java -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"