# Gestión y priorización de incidencias eléctricas — Backend y despliegue

Prototipo académico (UPC — Innovación y Transformación Digital) para **agrupar reportes eléctricos cercanos en incidencias revisables** por un operador, conservando siempre el reporte original y dejando la decisión final en manos de una persona.

Este repositorio contiene el entregable de **Persona 1: Backend + despliegue**.

---

## Qué incluye

- **API REST en Spring Boot 3.5 / Java 21** con validación, transacciones, paginación y manejo uniforme de errores.
- **Motor heurístico** por sector, ventana temporal y tipo de falla, con estado **Baja Certeza** y motivos explicables.
- **Integración HTTP** con el microservicio de embeddings (timeout, *cooldown* y degradación sin bloquear el alta).
- **Modelo PostgreSQL** con migraciones Flyway `V1` (base) y `V2` (asignación del técnico), relación 1:N incidencia–reporte.
- **Trazabilidad**: historial de vinculación, separación y reasignación; el UUID del reporte nunca cambia.
- **Docker Compose** con PostgreSQL, backend, microservicio de embeddings y Nginx.
- **HTTPS** con Let's Encrypt o certificado autofirmado, variables de entorno y *healthchecks*.
- **21 pruebas automatizadas** (reglas C1–C5, flujo completo C1/C2/C6/C7, contrato de embeddings y bandeja del técnico).

---

## Estructura

```text
luzdelsur-incidencias/
├── backend/                     API Spring Boot + pruebas + Dockerfile
│   ├── src/main/java/...        controllers, services, dominio, motor, embeddings
│   ├── src/main/resources/      application.yml + db/migration/V1__baseline.sql
│   └── src/test/...             pruebas del motor y de integración
├── deploy/                      Compose, Nginx, certificados, scripts, mock
│   ├── docker-compose.yml
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
# Editar .env: POSTGRES_PASSWORD obligatoria
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
```

La API queda en `http://localhost:8080`. Si el microservicio de embeddings no está disponible, el motor sigue funcionando y degrada la certeza a `MEDIA`.

---

## Endpoints principales

| Método | Ruta | Rol | Función |
| --- | --- | --- | --- |
| `POST` | `/api/v1/reports` | Cliente | Registra reporte y ejecuta el motor |
| `GET` | `/api/v1/reports/{id}` | Cliente | Estado comprensible |
| `GET` | `/api/v1/reports/mine` | Cliente identificado | Reportes propios paginados |
| `POST` | `/api/v1/reports/{id}/persistence` | Cliente | Reabre un caso resuelto (C7) |
| `GET` | `/api/v1/operator/incidents` | Operador | Bandeja priorizada |
| `POST` | `/api/v1/operator/incidents/{id}/confirm` | Operador | Confirma agrupación |
| `POST` | `/api/v1/operator/reports/{id}/separate` | Operador | Separa reporte |
| `POST` | `/api/v1/operator/reports/{id}/reassign` | Operador | Reasigna a otra incidencia |
| `GET` | `/api/v1/technician/incidents` | Técnico | Bandeja de casos confirmados/en atención |
| `GET` | `/api/v1/technician/incidents/{id}` | Técnico | Contexto consolidado, asignación y coordenadas |
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
| `APP_DEV_DEFAULT_ROLE` | `OPERADOR` | Rol temporal sin JWT |
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
- `HttpDescriptionSimilarityClientTest`: contrato HTTP y enfriamiento ante fallos.
- `SchemaValidationTest`: Flyway `V1`+`V2` y mapeo JPA.

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

- **Persona 3:** JWT, roles reales, auditoría y migraciones `V2+`; dataset C1–C7. Ver [`docs/integracion-equipo.md`](docs/integracion-equipo.md).
- **Persona 5:** microservicio real de embeddings respetando el contrato. El mock actual es solo para demo.
- **Personas 2 y 4:** frontend y QA; la API ya expone todos los flujos.

---

## Estado

- Backend compilado y probado (`21/21` pruebas).
- Despliegue documentado y reproducible.
- Seguridad productiva (JWT) pendiente por diseño del reparto de tareas.
