#!/usr/bin/env bash
# Bootstrap de Gradle 8.7. Remplace gradle-wrapper.jar lorsque celui-ci n'est pas fourni.
set -euo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)"
if command -v gradle >/dev/null 2>&1; then exec gradle "$@"; fi
DIST="$DIR/.gradle-bootstrap"
if [ ! -x "$DIST/gradle-8.7/bin/gradle" ]; then
  mkdir -p "$DIST"
  if command -v curl >/dev/null 2>&1; then
    curl --fail --location --retry 3 https://services.gradle.org/distributions/gradle-8.7-bin.zip -o "$DIST/gradle.zip"
  elif command -v wget >/dev/null 2>&1; then
    wget -O "$DIST/gradle.zip" https://services.gradle.org/distributions/gradle-8.7-bin.zip
  else echo 'Installer Gradle 8.7 ou curl/wget.' >&2; exit 1; fi
  unzip -q "$DIST/gradle.zip" -d "$DIST"
  rm "$DIST/gradle.zip"
fi
exec "$DIST/gradle-8.7/bin/gradle" "$@"
