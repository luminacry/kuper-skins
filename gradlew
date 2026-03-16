#!/bin/sh

set -eu

APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)

if [ -z "${JAVA_HOME:-}" ] && [ -x "/c/Users/HUA/.jdks/openjdk-25.0.2/bin/java" ]; then
  JAVA_HOME="/c/Users/HUA/.jdks/openjdk-25.0.2"
fi

if [ -n "${JAVA_HOME:-}" ]; then
  JAVA_EXE="$JAVA_HOME/bin/java"
else
  JAVA_EXE="java"
fi

exec "$JAVA_EXE" -Xmx64m -Xms64m -classpath "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"
