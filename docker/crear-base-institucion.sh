#!/bin/sh
set -eu

# Escapar el nombre como identificador SQL y crear solo si no existe.
psql -v ON_ERROR_STOP=1 -v nombre="$INSTITUCION_DB_NAME" <<'SQL'
SELECT format('CREATE DATABASE %I', :'nombre')
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = :'nombre')
\gexec
SQL
