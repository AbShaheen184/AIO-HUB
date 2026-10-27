#!/usr/bin/env bash
# Build helper for the CinemaHQ replica.
# Pins the JDK and Android SDK so the build does not depend on shell profile state.
set -euo pipefail

export JAVA_HOME="${JAVA_HOME:-C:/Program Files/Java/jdk-27.0.1}"
export ANDROID_HOME="${ANDROID_HOME:-C:/Users/abuza/AppData/Local/Android/Sdk}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$PROJECT_DIR"

TASK="${1:-:app:assembleDebug}"
shift || true

./gradlew "$TASK" --no-daemon "$@"
