#!/bin/sh
set -eu

# %I y %L escapan el identificador y el literal; \gexec ejecuta solo si falta.
psql -v ON_ERROR_STOP=1 -v nombre="$INSTITUCION_DB_NAME" <<'SQL'
SELECT format('CREATE DATABASE %I', :'nombre')
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = :'nombre')
\gexec
SQL
