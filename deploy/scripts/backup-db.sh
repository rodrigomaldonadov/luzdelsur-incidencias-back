#!/usr/bin/env bash
# Respaldo comprimido de PostgreSQL con retencion de 7 dias.
# Usa las variables POSTGRES_* definidas dentro del contenedor de base de datos.
set -euo pipefail

cd "$(dirname "$0")/.."
mkdir -p backups

STAMP="$(date +%Y%m%d-%H%M%S)"
FILE="backups/incidencias-${STAMP}.sql.gz"

docker compose exec -T db sh -c \
  'PGPASSWORD="$POSTGRES_PASSWORD" pg_dump -h 127.0.0.1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"' \
  | gzip > "${FILE}"

find backups -name 'incidencias-*.sql.gz' -mtime +7 -delete
echo "Respaldo generado: ${FILE}"
