#!/bin/sh
set -eu
APP_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
if [ ! -f "$APP_DIR/target/app-materiales.jar" ]; then
  echo "Primero compilá: mvn -f \"$APP_DIR/pom.xml\" clean verify" >&2
  exit 1
fi
cd "$APP_DIR/.."
exec java -jar "$APP_DIR/target/app-materiales.jar" "$@"
