#!/bin/sh
# Maven Wrapper script – downloads Maven 3.9.9 on first run.
# Requires Java 21+.

set -e

MAVEN_WRAPPER_JAR=".mvn/wrapper/maven-wrapper.jar"
MAVEN_PROJECT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)

if [ ! -f "$MAVEN_WRAPPER_JAR" ]; then
    WRAPPER_URL=$(grep wrapperUrl .mvn/wrapper/maven-wrapper.properties | cut -d= -f2-)
    if command -v curl > /dev/null 2>&1; then
        curl -sL "$WRAPPER_URL" -o "$MAVEN_WRAPPER_JAR"
    elif command -v wget > /dev/null 2>&1; then
        wget -q "$WRAPPER_URL" -O "$MAVEN_WRAPPER_JAR"
    else
        echo "Error: curl or wget is required to download the Maven Wrapper."
        exit 1
    fi
fi

exec java -Dmaven.multiModuleProjectDirectory="$MAVEN_PROJECT_DIR" \
    -cp "$MAVEN_WRAPPER_JAR" org.apache.maven.wrapper.MavenWrapperMain "$@"
