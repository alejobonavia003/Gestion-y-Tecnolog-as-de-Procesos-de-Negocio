#!/usr/bin/env bash
# Despliega Organizacion_de_cursos.bar en Flowable UI sin pasar por el Modeler
# (el "Publish" del Modeler 6.8.0 convierte las asociaciones de compensación en
# sequenceFlow; ver README.md) y verifica que la versión nueva de `process` tenga
# exactamente 3 <association>.
#
# Uso:
#   ./desplegar.sh [ruta/al/archivo.bar]
#
# Variables de entorno (con sus valores por defecto):
#   FLOWABLE_URL           http://localhost:8080/flowable-ui
#   FLOWABLE_REST_USUARIO  admin
#   FLOWABLE_REST_CLAVE    test
#   FLOWABLE_PROCESO_CLAVE process
#   ASOCIACIONES_ESPERADAS 3
#
# Requiere: bash, curl y python3 (solo biblioteca estándar).

set -euo pipefail

CARPETA="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BAR="${1:-$CARPETA/Organizacion_de_cursos.bar}"
URL="${FLOWABLE_URL:-http://localhost:8080/flowable-ui}"
URL="${URL%/}"
USUARIO="${FLOWABLE_REST_USUARIO:-admin}"
CLAVE="${FLOWABLE_REST_CLAVE:-test}"
PROCESO="${FLOWABLE_PROCESO_CLAVE:-process}"
ESPERADAS="${ASOCIACIONES_ESPERADAS:-3}"

error() { echo "ERROR: $*" >&2; exit 1; }

[ -f "$BAR" ] || error "no existe el archivo $BAR"
command -v curl >/dev/null || error "falta curl"
command -v python3 >/dev/null || error "falta python3"

# Campo de un JSON leído por stdin: json_campo 'data[0].version'
json_campo() {
  python3 -c '
import json, sys
valor = json.load(sys.stdin)
for parte in sys.argv[1].replace("]", "").replace("[", ".").split("."):
    if parte == "":
        continue
    try:
        valor = valor[int(parte)] if isinstance(valor, list) else valor[parte]
    except (KeyError, IndexError, TypeError):
        valor = ""
        break
print("" if valor is None else valor)
' "$1"
}

ultima_definicion() {
  curl -sS --fail -u "$USUARIO:$CLAVE" \
    "$URL/process-api/repository/process-definitions?key=$PROCESO&latest=true"
}

echo "Flowable: $URL (usuario $USUARIO)"
echo "Archivo:  $BAR"

ANTES="$(ultima_definicion 2>/dev/null | json_campo 'data[0].version' || true)"
echo "Versión latest de '$PROCESO' antes de desplegar: ${ANTES:-ninguna}"

# 1) Despliegue del .bar por la API de apps
RESPUESTA="$(mktemp)"
trap 'rm -f "$RESPUESTA"' EXIT
CODIGO="$(curl -sS -o "$RESPUESTA" -w '%{http_code}' -u "$USUARIO:$CLAVE" \
  -F "file=@$BAR;filename=$(basename "$BAR")" \
  "$URL/app-api/app-repository/deployments")" || error "no se pudo conectar con $URL"

if [ "$CODIGO" != "200" ] && [ "$CODIGO" != "201" ]; then
  cat "$RESPUESTA" >&2; echo >&2
  error "el despliegue respondió HTTP $CODIGO"
fi
echo "Despliegue de app creado: $(json_campo id < "$RESPUESTA") (HTTP $CODIGO)"

# 2) Verificación de la nueva versión latest
DEF="$(ultima_definicion)" || error "no se pudo consultar la definición de '$PROCESO'"
VERSION="$(echo "$DEF" | json_campo 'data[0].version')"
DEF_ID="$(echo "$DEF" | json_campo 'data[0].id')"
RECURSO="$(echo "$DEF" | json_campo 'data[0].resource')"
[ -n "$VERSION" ] || error "no hay definición de '$PROCESO' después de desplegar"
echo "Versión latest de '$PROCESO' ahora: $VERSION ($DEF_ID)"

if [ -n "$ANTES" ] && [ "$VERSION" -le "$ANTES" ]; then
  error "la versión no aumentó ($ANTES -> $VERSION): el motor no creó una definición nueva"
fi

DEPLOY_ID="$(echo "$DEF" | json_campo 'data[0].deploymentId')"
RECURSO_XML="$URL/process-api/repository/deployments/$DEPLOY_ID/resourcedata/${RECURSO##*/}"
XML="$(curl -sS --fail -u "$USUARIO:$CLAVE" "$RECURSO_XML")" \
  || error "no se pudo descargar el XML desplegado ($RECURSO_XML)"
CANTIDAD="$(printf '%s' "$XML" | { grep -o '<association ' || true; } | wc -l | tr -d ' ')"

if [ "$CANTIDAD" != "$ESPERADAS" ]; then
  echo >&2
  echo "############################################################" >&2
  echo "  La versión $VERSION de '$PROCESO' tiene $CANTIDAD <association>" >&2
  echo "  (se esperaban $ESPERADAS). La compensación NO va a funcionar:" >&2
  echo "  'Compensation activity could not be found'." >&2
  echo "  Corré: python3 corregir-asociaciones.py <modelo>.bpmn20.xml --bar" >&2
  echo "  y volvé a desplegar con este script." >&2
  echo "############################################################" >&2
  exit 2
fi

echo "OK: la versión $VERSION tiene $CANTIDAD <association> de compensación."
