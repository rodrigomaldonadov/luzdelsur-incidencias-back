#!/usr/bin/env bash
# Renueva el certificado y recarga nginx. Programar en cron (por ejemplo,
# dos veces al dia) o en un systemd timer.
set -euo pipefail

cd "$(dirname "$0")/.."
DOMAIN="$(grep -E '^DOMAIN=' .env | cut -d= -f2-)"

docker run --rm \
  -v "$PWD/certbot/www:/var/www/certbot" \
  -v "$PWD/certs:/etc/letsencrypt" \
  certbot/certbot renew --webroot -w /var/www/certbot --quiet

if [ -f "certs/live/${DOMAIN}/fullchain.pem" ]; then
  cp -L "certs/live/${DOMAIN}/fullchain.pem" certs/fullchain.pem
  cp -L "certs/live/${DOMAIN}/privkey.pem" certs/privkey.pem
fi

docker compose exec -T proxy nginx -s reload
