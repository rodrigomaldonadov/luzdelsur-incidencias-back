# Gestión y priorización de incidencias eléctricas — Backend y despliegue

Prototipo académico (UPC — Innovación y Transformación Digital) para **agrupar reportes eléctricos cercanos en incidencias revisables** por un operador, conservando siempre el reporte original y dejando la decisión final en manos de una persona.

Este repositorio contiene el entregable de **Persona 1: Backend + despliegue**.

---

## Qué incluye

- **API REST en Spring Boot 3.5 / Java 21** con validación, transacciones, paginación y manejo uniforme de errores.
- **Motor heurístico** por sector, ventana temporal y tipo de falla, con estado **Baja Certeza** y motivos explicables.
- **Integración HTTP** con el microservicio de embeddings (timeout, *cooldown* y degradación sin bloquear el alta).
- **Modelo PostgreSQL** con migraciones Flyway `V1` (base), `V2` (asignación del técnico), `V3` (usuarios), `V4` (auditoría e historial de estados) y `V5` (calificación), relación 1:N incidencia–reporte.
- **Autenticación JWT** (login, registro, refresco, `/me`) con contraseñas BCrypt y roles `CLIENTE`, `OPERADOR`, `TECNICO`.
- **Auditoría** de accesos y acciones, historial de estados con notas, panel de métricas y gestión de técnicos.
- **Trazabilidad**: historial de vinculación, separación y reasignación; el UUID del reporte nunca cambia.
- **Docker Compose** con PostgreSQL, backend, microservicio de embeddings y Nginx.
- **HTTPS** con Let's Encrypt o certificado autofirmado, variables de entorno y *healthchecks*.
- **25 pruebas automatizadas** (reglas C1–C5, flujo completo C1/C2/C6/C7, contrato de embeddings, bandeja del técnico, autenticación JWT y semilla de demo).

---

## Estructura

```text
luzdelsur-incidencias/
├── Dockerfile                   Imagen del backend (build desde la raíz)
├── backend/                     API Spring Boot + pruebas + Dockerfile alternativo
├── deploy/                      Compose, Nginx, scripts, mock
│   ├── docker-compose.yml
│   ├── docker-compose.external-db.yml   Solo backend + PostgreSQL externo
│   ├── docker-compose.dev.yml   demo con mock de embeddings
│   ├── nginx/                   TLS, rate limit, cabeceras de seguridad
│   ├── scripts/                 deploy, backup, certificados
│   └── mock-embeddings/         servicio temporal para demo
└── docs/
    ├── arquitectura.md          diagramas C4, modelo, estados, NFRs
    ├── justificacion-backend.md decisiones y alternativas
    ├── api.md                   contrato HTTP + contrato de embeddings
    ├── despliegue.md            guía paso a paso del servidor
    └── integracion-equipo.md    traspasos a Personas 2, 3, 4 y 5
```

---

## Inicio rápido (demo con Docker)

Requisitos: Docker Desktop / Docker Engine con Compose v2.

```bash
cd deploy
cp .env.example .env
# Editar .env: POSTGRES_PASSWORD, APP_JWT_SECRET y APP_DEMO_PASSWORD obligatorias
chmod +x scripts/*.sh
./scripts/gen-selfsigned-cert.sh localhost
docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d --build
```

Verificar:

```bash
curl -k https://localhost/health
curl -k https://localhost/api/v1/grouping/config
```

- API: `https://localhost/api/v1`
- Swagger: `https://localhost/docs` (en modo dev)
- Base de datos: solo red interna de Docker

En Windows con PowerShell, ejecutar los scripts desde Git Bash/WSL, o generar el certificado con:

```powershell
openssl req -x509 -nodes -newkey rsa:2048 -days 365 `
  -keyout deploy/certs/privkey.pem -out deploy/certs/fullchain.pem `
  -subj "/CN=localhost" -addext "subjectAltName=DNS:localhost,IP:127.0.0.1"
```

---

## Backend sin Docker

Requisitos: JDK 21+, PostgreSQL 16+ (o el contenedor `db`).

```bash
cd backend
./mvnw test          # Windows: mvnw.cmd test
./mvnw spring-boot:run
```

Variables mínimas:

```bash
DB_URL=jdbc:postgresql://localhost:5432/incidencias
DB_USER=incidencias
DB_PASSWORD=incidencias
APP_JWT_SECRET=una-clave-local-de-al-menos-32-caracteres
```

Al arrancar se crean las cuentas de demo (`operador@`, `tecnico@`, `tecnico2@` y `cliente@luzdelsur.com.pe`, contraseña `12345678` en local) y, si la base está vacía, un dataset con casos en cada estado. Se desactiva con `APP_DEMO_SEED_USERS=false` / `APP_DEMO_SEED_REPORTS=false`.

La API queda en `http://localhost:8080`. Si el microservicio de embeddings no está disponible, el motor sigue funcionando y degrada la certeza a `MEDIA`.

---

## Despliegue solo backend con PostgreSQL externo

Para usar una base gestionada o un PostgreSQL ya existente, sin levantar el contenedor `db`:

```bash
cd deploy
cp .env.external.example .env
# Editar DB_URL, DB_USER, DB_PASSWORD y BACKEND_PORT
docker compose -f docker-compose.external-db.yml up -d --build
```

- **Puerto publicado:** `8080` (`http://IP_DEL_SERVIDOR:8080`). Se cambia con `BACKEND_PORT`.
- Swagger: `http://IP:8080/docs`, habilitando `SPRINGDOC_ENABLED=true`.
- Flyway aplica `V1` y `V2` al arrancar el contenedor.
- El Dockerfile del backend está en la raíz (`Dockerfile`); también existe `backend/Dockerfile`.
- Si se usa el stack completo con Nginx, los puertos públicos son **80/443** y el backend queda interno en 8080.

> No subas `deploy/.env` al repositorio: contiene las credenciales de la base.

---

## Endpoints principales

| Método | Ruta | Rol | Función |
| --- | --- | --- | --- |
| `POST` | `/api/v1/reports` | Cliente | Registra reporte y ejecuta el motor |
| `GET` | `/api/v1/reports/{id}` | Cliente | Estado comprensible |
| `POST` | `/api/v1/auth/login` · `/register` · `/refresh` | Público | Sesión JWT |
| `GET` | `/api/v1/auth/me` | Con sesión | Usuario actual |
| `GET` | `/api/v1/reports/mine` | Cliente con sesión | Reportes propios paginados |
| `POST` | `/api/v1/reports/{id}/feedback` | Dueño del reporte | Calificación de la atención |
| `POST` | `/api/v1/reports/{id}/persistence` | Cliente | Reabre un caso resuelto (C7) |
| `GET` | `/api/v1/operator/incidents` | Operador | Bandeja priorizada |
| `POST` | `/api/v1/operator/incidents/{id}/confirm` | Operador | Confirma agrupación |
| `POST` | `/api/v1/operator/reports/{id}/separate` | Operador | Separa reporte |
| `POST` | `/api/v1/operator/reports/{id}/reassign` | Operador | Reasigna a otra incidencia |
| `POST` | `/api/v1/operator/incidents/{id}/assign` | Operador | Asigna un técnico |
| `GET` | `/api/v1/operator/metrics` | Operador | Panel de monitoreo |
| `GET` `POST` `PATCH` | `/api/v1/operator/technicians` | Operador | Gestión de técnicos |
| `GET` | `/api/v1/operator/audit-events` | Operador | Auditoría |
| `GET` | `/api/v1/technician/incidents` | Técnico | Bandeja de casos confirmados/en atención |
| `GET` | `/api/v1/technician/incidents/{id}` | Técnico | Contexto consolidado, asignación y coordenadas |
| `GET` | `/api/v1/technician/incidents/history` | Técnico | Tareas cumplidas |
| `GET` | `/api/v1/technician/incidents/{id}/timeline` | Técnico | Historial con notas de estado |
| `POST` | `/api/v1/grouping/preview` | Operador | Evalúa reglas sin persistir |
| `GET` | `/actuator/health` | Interno | Salud |

Contrato completo, ejemplos y errores: [`docs/api.md`](docs/api.md).

---

## Variables de entorno clave

| Variable | Por defecto | Descripción |
| --- | --- | --- |
| `DB_URL` | `jdbc:postgresql://localhost:5432/incidencias` | Conexión JDBC |
| `GROUPING_WINDOW_MINUTES` | `30` | Ventana temporal del motor |
| `EMBEDDINGS_ENABLED` | `true` | Activa la señal semántica |
| `EMBEDDINGS_BASE_URL` | `http://localhost:8000` | URL del microservicio |
| `EMBEDDINGS_TIMEOUT_MS` | `1500` | Timeout por llamada |
| `EMBEDDINGS_MATCH_THRESHOLD` | `0.55` | Umbral de coincidencia |
| `APP_CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | Orígenes del frontend |
| `APP_JWT_SECRET` | aleatorio por arranque | Secreto HS256 (mínimo 32 caracteres; obligatorio en Compose) |
| `APP_JWT_ACCESS_TTL_MINUTES` | `60` | Vigencia del token de acceso |
| `APP_AUTH_ALLOW_STAFF_REGISTRATION` | `false` | Permite autoregistro de operador/técnico |
| `APP_DEMO_SEED_USERS` / `APP_DEMO_SEED_REPORTS` | `true` | Cuentas y dataset de demo |
| `APP_DEMO_PASSWORD` | `12345678` (local) | Contraseña de las cuentas de demo |
| `APP_ALLOW_DEV_HEADERS` | `false` | Acepta `X-Actor-*` sin token (solo pruebas locales) |
| `SPRINGDOC_ENABLED` | `true` | Swagger (desactivar en producción) |

Todas las variables del despliegue están en [`deploy/.env.example`](deploy/.env.example) y documentadas en [`docs/despliegue.md`](docs/despliegue.md).

---

## Pruebas

```bash
cd backend
./mvnw test
```

Cobertura funcional:

- `GroupingEngineTest`: C1, C2, C3/C4, C5, degradación por embeddings.
- `ReportFlowIntegrationTest`: flujo completo, separación/reasignación, C7, validaciones, autorización por rol, reportes propios y bandeja/asignación del técnico.
- `AuthFlowIntegrationTest`: registro, login, refresco, `/me`, cabeceras ignoradas en modo estricto y flujo completo con JWT (asignación, historial, calificación, métricas, auditoría).
- `DemoDataSeederTest`: la semilla corre sobre Flyway y deja cuentas y casos en cada estado.
- `HttpDescriptionSimilarityClientTest`: contrato HTTP y enfriamiento ante fallos.
- `SchemaValidationTest`: Flyway `V1`–`V5` y mapeo JPA.

---

## Decisiones de diseño (resumen)

| Decisión | Motivo |
| --- | --- |
| Reglas heurísticas + revisión humana | Explicables y alineadas al informe; sin datos históricos para un modelo supervisado |
| Embeddings como señal auxiliar | Mejora la certeza sin bloquear el registro ni automatizar la decisión |
| El reporte se guarda antes de agrupar | Ningún fallo posterior pierde el dato del ciudadano |
| Historial de vínculos | Corregir sin perder trazabilidad (H2) |
| Nginx + red interna | Solo 80/443 expuestos; base y embeddings invisibles |
| Compose en lugar de Kubernetes | Suficiente para un servidor propio y un prototipo |

Justificación completa: [`docs/justificacion-backend.md`](docs/justificacion-backend.md).

---

## Traspasos pendientes

- **Persona 5:** microservicio real de embeddings respetando el contrato. El mock actual es solo para demo.
- **Personas 2 y 4:** QA de las vistas conectadas; pantallas de métricas y gestión de técnicos (la API ya está). Ver [`docs/integracion-equipo.md`](docs/integracion-equipo.md).

---

## Estado

- Backend compilado y probado (`25/25` pruebas).
- Despliegue documentado y reproducible.
- Autenticación JWT, auditoría y semilla de demo en la rama `develop`.
