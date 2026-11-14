#!/usr/bin/env bash
# Emite un certificado Let's Encrypt usando el reto webroot.
#
# Requisitos:
#   1. DNS del dominio apuntando al servidor.
#   2. Puerto 80 accesible desde Internet.
#   3. Nginx ya levantado con un certificado temporal (gen-selfsigned-cert.sh).
#
# Uso: ./scripts/init-letsencrypt.sh correo@dominio.com
set -euo pipefail

cd "$(dirname "$0")/.."

if [ ! -f .env ]; then
  echo "Falta deploy/.env" >&2
  exit 1
fi

EMAIL="${1:-}"
DOMAIN="$(grep -E '^DOMAIN=' .env | cut -d= -f2-)"

if [ -z "${EMAIL}" ] || [ -z "${DOMAIN}" ] || [ "${DOMAIN}" = "localhost" ]; then
  echo "Uso: ./scripts/init-letsencrypt.sh correo@dominio.com (con DOMAIN configurado en .env)" >&2
  exit 1
fi

mkdir -p certbot/www certs

docker run --rm \
  -v "$PWD/certbot/www:/var/www/certbot" \
  -v "$PWD/certs:/etc/letsencrypt" \
  certbot/certbot certonly \
  --webroot -w /var/www/certbot \
  -d "${DOMAIN}" \
  --email "${EMAIL}" \
  --agree-tos --no-eff-email --non-interactive

cp -L "certs/live/${DOMAIN}/fullchain.pem" certs/fullchain.pem
cp -L "certs/live/${DOMAIN}/privkey.pem" certs/privkey.pem
chmod 600 certs/privkey.pem

docker compose exec -T proxy nginx -s reload
echo "Certificado Let's Encrypt instalado para ${DOMAIN}"
