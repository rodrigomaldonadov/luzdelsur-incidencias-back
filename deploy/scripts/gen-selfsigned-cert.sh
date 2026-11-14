#!/usr/bin/env bash
# Genera un certificado autofirmado para demos o pruebas internas.
# Uso: ./scripts/gen-selfsigned-cert.sh [dominio]
set -euo pipefail

cd "$(dirname "$0")/.."
DOMAIN="${1:-localhost}"

mkdir -p certs

openssl req -x509 -nodes -newkey rsa:2048 -days 365 \
  -keyout certs/privkey.pem \
  -out certs/fullchain.pem \
  -subj "/CN=${DOMAIN}" \
  -addext "subjectAltName=DNS:${DOMAIN},DNS:localhost,IP:127.0.0.1"

chmod 600 certs/privkey.pem
echo "Certificado autofirmado generado en deploy/certs para ${DOMAIN}"
echo "Recuerde que el navegador mostrara una advertencia de confianza."
