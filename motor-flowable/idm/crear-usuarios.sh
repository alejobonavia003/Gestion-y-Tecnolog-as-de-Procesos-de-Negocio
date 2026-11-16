#!/usr/bin/env bash
# =============================================================================
# Crea los usuarios y grupos del proyecto en Flowable IDM (por la API REST).
# Cada integrante lo corre UNA vez en su máquina, con Flowable levantado.
#
# Uso (desde la raíz del repo):
#   bash motor-flowable/idm/crear-usuarios.sh
#
# Lee FLOWABLE_REST_USUARIO y FLOWABLE_REST_CLAVE del .env de la raíz.
# Se puede correr más de una vez: lo que ya existe se saltea.
# =============================================================================
set -euo pipefail

RAIZ="$(cd "$(dirname "$0")/../.." && pwd)"
if [[ -f "$RAIZ/.env" ]]; then
  set -a
  # shellcheck disable=SC1091
  . <(sed 's/\r$//' "$RAIZ/.env")
  set +a
fi

FLOWABLE="${FLOWABLE_REST_URL:-http://localhost:8080/flowable-ui/process-api}"
ADMIN="${FLOWABLE_REST_USUARIO:?Falta FLOWABLE_REST_USUARIO en .env}"
ADMIN_CLAVE="${FLOWABLE_REST_CLAVE:?Falta FLOWABLE_REST_CLAVE en .env}"

# Contraseña de los 5 usuarios del proyecto (solo para desarrollo y la defensa).
CLAVE_USUARIOS="${CLAVE_USUARIOS:-1234}"

# --- Grupos: id | nombre -------------------------------------------------------
GRUPOS=(
  "Equipo-Administrativo|Equipo-Administrativo"
  "Equipo-Docente|Equipo-Docente"
)

# --- Usuarios: id | nombre | apellido | email | grupo ---------------------------
# Copiados de la configuración de Lauty. Los IDs de los jefes son los que usa el BPMN.
USUARIOS=(
  "Jefe-Administrativo|marcos|jefeadm|Jefeadm@gmail.com|Equipo-Administrativo"
  "empleado-administrativo|Hector|empleadoadm|Empleadoadm@gmail.com|Equipo-Administrativo"
  "Jefe-Docente|maria|jefe-docente|Jefedocente@gmail.com|Equipo-Docente"
  "docente-1|Gaston|docente-1|docente-1@gmail.com|Equipo-Docente"
  "docente-2|Juan|docente-2|docente-2@gmail.com|Equipo-Docente"
)

# POST con JSON; imprime el código HTTP
post() {
  curl -s -o /tmp/flowable-respuesta.json -w "%{http_code}" \
    -u "$ADMIN:$ADMIN_CLAVE" -H "Content-Type: application/json" \
    -X POST "$FLOWABLE$1" -d "$2"
}

informar() { # $1 = código HTTP, $2 = qué se hizo
  case "$1" in
    200|201) echo "  ✔ $2" ;;
    409)     echo "  • $2 (ya existía)" ;;
    *)       echo "  ✘ $2 → HTTP $1: $(cat /tmp/flowable-respuesta.json)"; exit 1 ;;
  esac
}

echo "Flowable: $FLOWABLE"
codigo=$(curl -s -o /dev/null -w "%{http_code}" -u "$ADMIN:$ADMIN_CLAVE" "$FLOWABLE/identity/groups?size=1")
if [[ "$codigo" != "200" ]]; then
  echo "No se pudo usar la API de Flowable (HTTP $codigo). ¿Está levantado? ¿Son correctas las credenciales del .env?"
  exit 1
fi

echo "Grupos:"
for g in "${GRUPOS[@]}"; do
  IFS='|' read -r id nombre <<< "$g"
  informar "$(post /identity/groups "{\"id\":\"$id\",\"name\":\"$nombre\",\"type\":\"assignment\"}")" "$id"
done

echo "Usuarios:"
for u in "${USUARIOS[@]}"; do
  IFS='|' read -r id nombre apellido email grupo <<< "$u"
  informar "$(post /identity/users "{\"id\":\"$id\",\"firstName\":\"$nombre\",\"lastName\":\"$apellido\",\"email\":\"$email\",\"password\":\"$CLAVE_USUARIOS\"}")" "$id"
  informar "$(post "/identity/groups/$grupo/members" "{\"userId\":\"$id\"}")" "$id → $grupo"
done

echo
echo "Listo. Los 5 usuarios tienen la clave: $CLAVE_USUARIOS"
