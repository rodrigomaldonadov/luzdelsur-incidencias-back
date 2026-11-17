# Guía de despliegue — servidor propio con Docker, HTTPS y variables de entorno

Esta guía cubre la puesta en marcha del stack completo: **PostgreSQL + backend Spring Boot + microservicio de embeddings + Nginx con TLS**. Está pensada para un servidor Linux (Ubuntu 22.04/24.04 o Debian 12) con acceso `sudo`.

---

## 0. Resumen ejecutable

Lo mínimo para dejar la API funcionando. El detalle de cada paso está en las secciones siguientes.

| # | Paso | Comando o acción | Cómo saber que salió bien |
| --- | --- | --- | --- |
| 1 | Docker y Compose v2 instalados | §2 | `docker compose version` responde |
| 2 | Clonar este repositorio y entrar a `deploy/` | `git clone … && cd luzdelsur-incidencias-back/deploy` | Existe `deploy/.env.example` |
| 3 | Crear `.env` | `cp .env.example .env && chmod 600 .env` | El archivo existe y **no** se versiona |
| 4 | Completar las 3 variables obligatorias | `POSTGRES_PASSWORD`, `APP_JWT_SECRET` (mín. 32 caracteres) y `APP_DEMO_PASSWORD` | Sin ellas `docker compose` aborta con `…_requerido` |
| 5 | Ajustar dominio y CORS | `DOMAIN`, `APP_PUBLIC_URL`, `APP_CORS_ALLOWED_ORIGINS` (URL exacta del frontend) | — |
| 6 | Certificado TLS | `./scripts/gen-selfsigned-cert.sh "$DOMAIN"` (luego Let's Encrypt, §5) | Existen `certs/fullchain.pem` y `certs/privkey.pem` |
| 7 | Levantar | Sin el repo de embeddings real: `docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d --build`. Con él en `../embeddings`: `./scripts/deploy.sh` | `docker compose ps` muestra `lds-backend` en `healthy` |
| 8 | Verificar | §6 «Verificación» (salud + login) | `/health` responde `UP` y el login devuelve `token` |
| 9 | Conectar el frontend | Compilar el front con `VITE_API_URL=https://<DOMINIO>/api` | El login del front entra con las cuentas demo |

Datos que conviene tener presentes:

- **Puerto y rutas.** El backend escucha en `8080` solo dentro de la red de Docker; afuera se expone únicamente Nginx en `80/443`. La API vive bajo `/api/v1/...` y la salud pública en `/health`.
- **Base de datos.** Flyway crea y migra el esquema al arrancar (`V1` a `V5`). No hay que ejecutar SQL a mano. Nunca editar una migración ya aplicada.
- **Datos de demostración.** En el primer arranque se crean las cuentas `operador@`, `tecnico@`, `tecnico2@` y `cliente@luzdelsur.com.pe`, todas con la clave `APP_DEMO_PASSWORD`, y un dataset de 15 reportes (casos C1-C5 más casos confirmados, en atención y resueltos). Los reportes solo se siembran si la tabla está vacía; las cuentas solo si no existen. Para no sembrar nada: `APP_DEMO_SEED_USERS=false` y `APP_DEMO_SEED_REPORTS=false`.
- **Autenticación.** JWT HS256. `POST /api/v1/auth/login` devuelve `token` (acceso, 60 min) y `refreshToken` (7 días). Las demás rutas esperan `Authorization: Bearer <token>`. El registro público crea solo cuentas `CLIENTE`.
- **Cambiar `APP_JWT_SECRET`** invalida todas las sesiones abiertas (los usuarios vuelven a iniciar sesión). No afecta los datos.
- **`APP_ALLOW_DEV_HEADERS` debe quedar en `false`.** En `true` cualquiera puede hacerse pasar por operador con cabeceras `X-Actor-*`; existe solo para pruebas automatizadas.

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
| `APP_JWT_SECRET` | salida de `openssl rand -base64 48` | **Sí** | Firma de los tokens; mínimo 32 caracteres o el backend no arranca |
| `APP_JWT_ACCESS_TTL_MINUTES` | `60` | No | Vigencia del token de acceso |
| `APP_JWT_REFRESH_TTL_DAYS` | `7` | No | Vigencia del token de refresco |
| `APP_AUTH_ALLOW_STAFF_REGISTRATION` | `false` | No | En `false` el registro público solo crea clientes |
| `APP_ALLOW_DEV_HEADERS` | `false` | No | Cabeceras `X-Actor-*` sin token; **mantener en `false`** |
| `APP_DEMO_SEED_USERS` | `true` | No | Crea las cuentas demo si no existen |
| `APP_DEMO_SEED_REPORTS` | `true` | No | Siembra el dataset demo si no hay reportes |
| `APP_DEMO_PASSWORD` | cadena aleatoria | **Sí** | Clave de las cuentas demo |
| `APP_DEV_DEFAULT_ROLE` | `CLIENTE` | No | Rol asumido sin token; solo aplica con `APP_ALLOW_DEV_HEADERS=true` |
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

# Login con una cuenta demo (la clave es APP_DEMO_PASSWORD)
TOKEN=$(curl -fsS -X POST https://TU_DOMINIO/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"operador@luzdelsur.com.pe","password":"<APP_DEMO_PASSWORD>"}' \
  | sed -E 's/.*"token":"([^"]+)".*/\1/')

# Sesión válida: debe responder el usuario con rol OPERADOR
curl -fsS https://TU_DOMINIO/api/v1/auth/me -H "Authorization: Bearer $TOKEN"

# Bandeja del operador: debe listar las incidencias sembradas
curl -fsS "https://TU_DOMINIO/api/v1/operator/incidents?size=5" -H "Authorization: Bearer $TOKEN"
```

Con certificado autofirmado agregar `-k` a cada `curl`.

### Conexión del frontend

El frontend (React + Vite) lee la URL de la API **en tiempo de compilación**:

```bash
VITE_API_URL=https://TU_DOMINIO/api npm run build
```

`VITE_API_URL` termina en `/api` (sin `/v1`). El origen desde donde se sirve el frontend debe figurar en `APP_CORS_ALLOWED_ORIGINS`; si se sirve desde el mismo dominio que la API, basta con ese dominio.

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

### 6.4 Solo backend con PostgreSQL externo

Si la base de datos ya existe (por ejemplo, un PostgreSQL gestionado), usar el compose específico:

```bash
cd deploy
cp .env.external.example .env
chmod 600 .env
# Editar DB_URL, DB_USER, DB_PASSWORD y BACKEND_PORT
docker compose -f docker-compose.external-db.yml up -d --build
docker compose -f docker-compose.external-db.yml ps
```

| Aspecto | Valor |
| --- | --- |
| Puerto público del backend | **8080** por defecto (`BACKEND_PORT`) |
| Contenedor | `8080` interno |
| Healthcheck | `GET /actuator/health/readiness` |
| Migraciones | Flyway aplica `V1` y `V2` al iniciar |
| Swagger | `http://IP:8080/docs` con `SPRINGDOC_ENABLED=true` |
| HTTPS | No incluido en este compose; usar un proxy externo o el compose completo (80/443) |

Variables mínimas de `.env`:

```env
BACKEND_PORT=8080
DB_URL=jdbc:postgresql://HOST:5435/incidencias
DB_USER=incidencias
DB_PASSWORD=...
SPRINGDOC_ENABLED=false
```

Abrir solo el puerto necesario en el firewall:

```bash
sudo ufw allow 8080/tcp
```

En producción se recomienda no exponer 8080 directamente: colocar Nginx/Caddy con TLS delante o usar `docker-compose.yml` (puertos 80/443, backend interno).

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
| `docker compose` aborta con `APP_JWT_SECRET_requerido` (o `APP_DEMO_PASSWORD_requerido`) | Variable obligatoria vacía en `.env` | Completarla y volver a levantar |
| Backend termina con `APP_JWT_SECRET debe tener al menos 32 caracteres` | Secreto corto | Generar uno con `openssl rand -base64 48` |
| El build falla en `embeddings`: `path ../embeddings not found` | No está el repositorio del microservicio real | Usar el override `docker-compose.dev.yml` (mock) o ajustar `EMBEDDINGS_CONTEXT` |
| Login responde `401` | Clave distinta a `APP_DEMO_PASSWORD` del **primer** arranque | Las cuentas demo no se recrean; usar la clave original o borrar las filas de `app_user` y reiniciar |
| Todas las rutas responden `401` | Falta `Authorization: Bearer` o el token venció | Volver a hacer login; el front refresca solo |
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
- [ ] `APP_JWT_SECRET` y `APP_DEMO_PASSWORD` propios del servidor, distintos a los de desarrollo.
- [ ] `APP_ALLOW_DEV_HEADERS=false` (es el valor por defecto en Compose).
- [ ] `APP_AUTH_ALLOW_STAFF_REGISTRATION=false`: los técnicos los da de alta un operador con `POST /api/v1/operator/technicians`; los operadores vienen de la semilla demo.
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
4. Logs mostrando `Flyway` aplicando `V1` a `V5` y el backend iniciando.
5. Alta de un reporte y bandeja del operador con la etiqueta de baja certeza (coordinarlo con Personas 2 y 4).
