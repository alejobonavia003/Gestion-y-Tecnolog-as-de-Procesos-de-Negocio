#!/bin/sh
set -eu
APP_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
if [ ! -x "$APP_DIR/target/app-materiales" ]; then
  echo "Primero compilá: mvn -f \"$APP_DIR/pom.xml\" compile" >&2
  exit 1
fi
exec "$APP_DIR/target/app-materiales" "$@"
