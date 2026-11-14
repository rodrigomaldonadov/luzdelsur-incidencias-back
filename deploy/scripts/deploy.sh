#!/usr/bin/env bash
# Despliegue simple en el servidor: reconstruye el backend y levanta el stack.
# Uso: ./scripts/deploy.sh
set -euo pipefail

cd "$(dirname "$0")/.."

if [ ! -f .env ]; then
  echo "Falta deploy/.env. Copie .env.example y configure los secretos." >&2
  exit 1
fi

docker compose build backend
docker compose up -d --remove-orphans
docker compose ps
echo "Estado de salud:"
curl -fsS "https://$(grep -E '^DOMAIN=' .env | cut -d= -f2-)/health" || true
echo
