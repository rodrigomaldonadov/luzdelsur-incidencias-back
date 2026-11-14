# Guía de despliegue — servidor propio con Docker, HTTPS y variables de entorno

Esta guía cubre la puesta en marcha del stack completo: **PostgreSQL + backend Spring Boot + microservicio de embeddings + Nginx con TLS**. Está pensada para un servidor Linux (Ubuntu 22.04/24.04 o Debian 12) con acceso `sudo`.

---

## 1. Requisitos

| Requisito | Mínimo | Recomendado |
| --- | --- | --- |
| CPU | 2 vCPU | 4 vCPU |
| RAM | 4 GB | 8 GB (el modelo de embeddings consume memoria) |
| Disco | 20 GB | 40 GB |
| SO | Linux con Docker | Ubuntu 24.04 LTS |
| Red | IP pública, puertos 80/443 abiertos | Dominio + DNS tipo A |
| Software | Docker Engine 24+, Docker Compose v2 | Git |

> En Windows se puede desarrollar con Docker Desktop, pero el despliegue institucional debe hacerse en Linux. Los scripts `.sh` requieren WSL/Git Bash o el servidor.

---

## 2. Preparación del servidor

```bash
# Docker + Compose
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker "$USER"
newgrp docker

# Utilidades
sudo apt-get update && sudo apt-get install -y git curl ufw

# Firewall: solo SSH y web
sudo ufw allow OpenSSH
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw enable
```

Buenas prácticas mínimas:

- Acceso por clave SSH, contraseña deshabilitada y `PermitRootLogin no`.
- `fail2ban` opcional para SSH y Nginx.
- Actualizaciones automáticas de seguridad.
- Usuario no root para desplegar.

---

## 3. Estructura y obtención del código

```text
luzdelsur-incidencias/
├── backend/                 # API Spring Boot (Dockerfile incluido)
├── deploy/                  # Compose, Nginx, scripts, .env, certificados
│   ├── docker-compose.yml
│   ├── docker-compose.dev.yml
│   ├── nginx/
│   ├── scripts/
│   ├── mock-embeddings/
│   └── .env.example
└── docs/
```

El repositorio del microservicio real (Persona 5) debe ubicarse como carpeta hermana `../embeddings`, o ajustar `EMBEDDINGS_CONTEXT` en `.env`.

```bash
git clone <URL_DEL_REPOSITORIO>
cd luzdelsur-incidencias/deploy
cp .env.example .env
chmod 600 .env
chmod +x scripts/*.sh
nano .env
```

---

## 4. Variables de entorno

| Variable | Ejemplo | Obligatoria | Descripción |
| --- | --- | --- | --- |
| `DOMAIN` | `incidencias.midominio.com` | Sí | Dominio del certificado y `server_name` |
| `APP_PUBLIC_URL` | `https://incidencias.midominio.com` | Sí | URL pública usada por la app |
| `APP_CORS_ALLOWED_ORIGINS` | `https://app.midominio.com` | Sí | Orígenes permitidos, separados por coma |
| `POSTGRES_DB` | `incidencias` | Sí | Nombre de la base |
| `POSTGRES_USER` | `incidencias` | Sí | Usuario de base |
| `POSTGRES_PASSWORD` | cadena larga aleatoria | Sí | Secreto; no versionar |
| `DB_POOL_SIZE` | `10` | No | Conexiones por instancia |
| `GROUPING_WINDOW_MINUTES` | `30` | No | Ventana temporal de agrupación |
| `GROUPING_MAX_CANDIDATES` | `5` | No | Casos evaluados por reporte |
| `EMBEDDINGS_ENABLED` | `true` | No | Activa la señal semántica |
| `EMBEDDINGS_CONTEXT` | `../embeddings` | No | Ruta del repositorio de Persona 5 |
| `EMBEDDINGS_TIMEOUT_MS` | `1500` | No | Timeout por llamada |
| `EMBEDDINGS_COOLDOWN_SECONDS` | `30` | No | Espera tras un fallo |
| `EMBEDDINGS_MATCH_THRESHOLD` | `0.55` | No | Similitud que confirma certeza alta |
| `EMBEDDINGS_CONFLICT_THRESHOLD` | `0.30` | No | Similitud que degrada a baja certeza |
| `APP_DEV_DEFAULT_ROLE` | `CLIENTE` | No | Rol temporal sin cabecera (hasta JWT) |
| `SPRINGDOC_ENABLED` | `false` | No | Swagger en producción |
| `APP_LOG_LEVEL` | `INFO` | No | Nivel de logs del paquete propio |
| `JAVA_OPTS` | `-XX:MaxRAMPercentage=75` | No | Memoria de la JVM |

Generar una clave segura:

```bash
openssl rand -base64 32
```

---

## 5. Certificados TLS

### Opción A — Let's Encrypt (recomendado en producción)

1. Apuntar el DNS del dominio a la IP del servidor.
2. Generar un certificado temporal para poder iniciar Nginx:

```bash
./scripts/gen-selfsigned-cert.sh "$(grep -E '^DOMAIN=' .env | cut -d= -f2-)"
```

3. Levantar el stack (§6).
4. Emitir el certificado real:

```bash
./scripts/init-letsencrypt.sh correo@midominio.com
```

El script usa el reto `webroot`, copia `fullchain.pem` y `privkey.pem` a `deploy/certs` y recarga Nginx.

5. Renovación automática (cron dos veces al día):

```cron
17 3,15 * * * cd /opt/luzdelsur-incidencias/deploy && ./scripts/renew-certs.sh >> backups/renew.log 2>&1
```

### Opción B — autofirmado (solo demo)

```bash
./scripts/gen-selfsigned-cert.sh incidencias.midominio.com
```

El navegador mostrará una advertencia. No usar en una exposición real.

> Nginx exige que los archivos `certs/fullchain.pem` y `certs/privkey.pem` existan **antes** de arrancar. Por eso el certificado temporal se genera primero.

---

## 6. Puesta en marcha

### Demo local con mock de embeddings

```bash
cd deploy
docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d --build
docker compose ps
curl -k https://localhost/health
```

El mock implementa el mismo contrato HTTP que el servicio real, con similitud léxica. Sirve para validar el flujo sin descargar el modelo.

### Producción con el microservicio real (Persona 5)

```bash
cd deploy
./scripts/deploy.sh
```

Equivale a:

```bash
docker compose build backend
docker compose up -d --remove-orphans
docker compose ps
```

El arranque espera a que PostgreSQL esté saludable y a que el backend responda `readiness`.

### Verificación

```bash
# Salud pública (sin detalles internos)
curl -fsS https://TU_DOMINIO/health

# Swagger solo si SPRINGDOC_ENABLED=true
curl -fsS https://TU_DOMINIO/docs >/dev/null && echo "docs OK"

# Alta de prueba
curl -X POST https://TU_DOMINIO/api/v1/reports \
  -H "Content-Type: application/json" \
  -H "X-Actor-Role: CLIENTE" \
  -d '{"sector":"San Isidro","failureType":"CORTE_TOTAL","description":"No hay luz en la cuadra"}'
```

### Desarrollo del backend sin contenedor

```bash
# Base y embeddings mock en Docker
cd deploy
docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d db embeddings

# Backend en la máquina (Windows: mvnw.cmd)
cd ../backend
./mvnw spring-boot:run
```

Con `application.yml` por defecto, la API queda en `http://localhost:8080` y usa `jdbc:postgresql://localhost:5432/incidencias`. Si la base no publica el puerto, ejecutar todo con Compose o ajustar `DB_URL`.

---

## 7. Operación

| Tarea | Comando |
| --- | --- |
| Ver estado | `docker compose ps` |
| Logs del backend | `docker compose logs -f --tail=200 backend` |
| Logs de Nginx | `docker compose logs -f --tail=200 proxy` |
| Reiniciar backend | `docker compose restart backend` |
| Reconstruir tras cambios | `docker compose build backend && docker compose up -d backend` |
| Detener sin borrar datos | `docker compose down` |
| Detener y borrar datos | `docker compose down -v` ⚠️ |
| Consola SQL | `docker compose exec db sh -c 'psql -h 127.0.0.1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'` |
| Respaldo | `./scripts/backup-db.sh` |
| Restaurar | `gunzip -c backups/archivo.sql.gz \| docker compose exec -T db sh -c 'PGPASSWORD="$POSTGRES_PASSWORD" psql -h 127.0.0.1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'` |

### Actualización y reversión

```bash
# Actualizar
git pull
docker compose build backend
docker compose up -d backend
docker compose ps

# Revertir a una imagen anterior (si se etiqueta)
docker tag luzdelsur/incidencias-backend:latest luzdelsur/incidencias-backend:rollback
```

Las migraciones Flyway son incrementales. **Nunca** editar una migración ya aplicada: crear `V2`, `V3`, etc.

### Respaldos

- Automatizar `backup-db.sh` con cron diario.
- Copiar `deploy/backups` y `deploy/certs` fuera del servidor.
- Probar la restauración al menos una vez antes de la demo.

---

## 8. Diagnóstico de problemas

| Síntoma | Causa probable | Solución |
| --- | --- | --- |
| Nginx no arranca: `cannot load certificate` | Faltan `certs/fullchain.pem` o `privkey.pem` | Ejecutar `gen-selfsigned-cert.sh` o revisar la emisión |
| `502 Bad Gateway` | Backend no saludable o reiniciando | `docker compose logs backend`; esperar `start_period` |
| Backend reinicia en bucle | Credenciales de base incorrectas | Revisar `.env` y `DB_URL`; probar `pg_isready` |
| `429 Too Many Requests` | Rate limit de Nginx | Ajustar `limit_req_zone` o espaciar solicitudes |
| Alta de reporte responde `500` al llamar embeddings | Microservicio caído con excepción no controlada | Ver logs; el cliente ya degrada, pero revisar la URL interna |
| `spring.jpa.hibernate.ddl-auto=validate` falla | Esquema desactualizado | Ejecutar Flyway (`docker compose up backend` aplica migraciones) |
| CORS bloquea el frontend | Origen no listado | Actualizar `APP_CORS_ALLOWED_ORIGINS` y reiniciar backend |
| Certificado vencido | Renovación no programada | Ejecutar `renew-certs.sh` y revisar el cron |
| Contenedor de embeddings agota RAM | Modelo grande | Limitar réplicas, usar modelo más pequeño o más RAM |

Comandos útiles:

```bash
docker compose config                 # validar variables resueltas
docker inspect --format '{{.State.Health.Status}}' lds-backend
docker stats --no-stream
```

---

## 9. Checklist de seguridad antes de exponer

- [ ] `.env` con permisos `600` y fuera de Git.
- [ ] Contraseña de PostgreSQL larga y distinta a la de desarrollo.
- [ ] TLS válido, HTTP redirigiendo a HTTPS.
- [ ] `SPRINGDOC_ENABLED=false` en producción.
- [ ] `APP_DEV_DEFAULT_ROLE` eliminado cuando Persona 3 implemente JWT.
- [ ] `/actuator` bloqueado en Nginx (solo `/health`).
- [ ] Puertos de base y embeddings **no** publicados al host.
- [ ] Firewall con solo 22/80/443.
- [ ] Respaldos automáticos y restauración probada.
- [ ] Logs con rotación (`max-size`/`max-file` configurados).
- [ ] Ley 29733: descripciones sin datos personales innecesarios, acceso por rol y retención definida.

---

## 10. Evidencia para el informe

Para la sección de arquitectura y la demo, capturar:

1. `docker compose ps` con los cuatro servicios en `healthy`/`running`.
2. `curl https://<dominio>/health` respondiendo `{"status":"UP"}`.
3. Candado de HTTPS en el navegador (o salida de `openssl s_client`).
4. Logs mostrando `Flyway` aplicando `V1` y el backend iniciando.
5. Alta de un reporte y bandeja del operador con la etiqueta de baja certeza (coordinarlo con Personas 2 y 4).
