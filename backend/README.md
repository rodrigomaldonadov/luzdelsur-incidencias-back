# Backend — API y motor heurístico

API REST en **Spring Boot 3.5 / Java 21** para registrar reportes eléctricos, proponer agrupaciones revisables y gestionar su ciclo de vida.

La documentación de arquitectura, contrato HTTP y despliegue está en [`../docs/`](../docs/).

---

## Compilar y ejecutar

```bash
./mvnw test            # Windows: mvnw.cmd test
./mvnw spring-boot:run # API en http://localhost:8080
```

Requisitos: JDK 21+ y PostgreSQL 16+ (o el contenedor `db` del Compose).

Variables mínimas:

```bash
DB_URL=jdbc:postgresql://localhost:5432/incidencias
DB_USER=incidencias
DB_PASSWORD=incidencias
```

Flyway aplica `src/main/resources/db/migration/V1__baseline.sql` y `V2__technician_assignment.sql` al iniciar. `spring.jpa.hibernate.ddl-auto=validate` impide que Hibernate modifique el esquema.

---

## Estructura de paquetes

```text
pe.edu.upc.luzdelsur.incidencias
├── api/            controladores, DTOs y mapper de respuestas
├── config/         propiedades tipadas, CORS, OpenAPI, reloj
├── common/         errores uniformes, paginación, normalización de texto
├── incident/       incidencia, estados, prioridad, métricas y ciclo de vida
├── report/         reporte original, vínculos e historial
├── grouping/       motor heurístico y carga de candidatos
├── embeddings/     puerto + cliente HTTP tolerante a fallos
└── security/       identidad temporal por cabeceras (JWT: Persona 3)
```

Regla de dependencias: `api → servicios → repositorios`; `grouping` no conoce HTTP ni JPA. El motor recibe datos y devuelve una propuesta con motivos.

---

## Pruebas

```bash
./mvnw test
```

| Prueba | Qué valida |
| --- | --- |
| `GroupingEngineTest` | C1, C2, C3/C4, C5, degradación por embeddings |
| `ReportFlowIntegrationTest` | Flujo completo, autorización por rol, separación, reasignación, C7, reportes propios y bandeja/asignación del técnico |
| `HttpDescriptionSimilarityClientTest` | Contrato HTTP, filtrado de valores y enfriamiento ante fallos |
| `SchemaValidationTest` | Flyway `V1`+`V2` + mapeo JPA (`ddl-auto=validate`) |

Resultado esperado: **21 pruebas, 0 fallos**.

---

## Configuración

Todo se controla por variables de entorno (ver [`../deploy/.env.example`](../deploy/.env.example)):

| Variable | Default | Uso |
| --- | --- | --- |
| `GROUPING_WINDOW_MINUTES` | `30` | Ventana temporal de agrupación |
| `GROUPING_MAX_CANDIDATES` | `5` | Casos evaluados por reporte |
| `EMBEDDINGS_ENABLED` | `true` | Señal semántica |
| `EMBEDDINGS_BASE_URL` | `http://localhost:8000` | Microservicio de Persona 5 |
| `EMBEDDINGS_TIMEOUT_MS` | `1500` | Timeout por llamada |
| `EMBEDDINGS_COOLDOWN_SECONDS` | `30` | Enfriamiento tras fallo |
| `EMBEDDINGS_MATCH_THRESHOLD` | `0.55` | Umbral de certeza alta |
| `EMBEDDINGS_CONFLICT_THRESHOLD` | `0.30` | Umbral de baja certeza |
| `APP_CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | Orígenes del frontend |
| `APP_DEV_DEFAULT_ROLE` | `OPERADOR` | Rol temporal sin JWT |
| `SPRINGDOC_ENABLED` | `true` | Swagger UI en `/docs` |

---

## Endpoints

Ver [`../docs/api.md`](../docs/api.md). Resumen:

- Cliente: `POST /api/v1/reports`, `GET /api/v1/reports/{id}`, `GET /api/v1/reports/mine`, `POST /api/v1/reports/{id}/persistence`
- Operador: bandeja, detalle, confirmar, cambiar estado, separar, reasignar
- Técnico: bandeja (`onlyMine`), contexto del caso y cambio a `EN_ATENCION`/`RESUELTA`
- Motor: `POST /api/v1/grouping/preview`, `GET /api/v1/grouping/config`

---

## Notas de seguridad

- `X-Actor-Id` / `X-Actor-Role` son **temporales**. Persona 3 implementa JWT reemplazando `HeaderActorProvider`; los controladores ya exigen roles a través de `AccessControl`.
- Los reportes nunca se eliminan: separar o reasignar solo cambia el vínculo y deja historial.
- En producción, mantener `SPRINGDOC_ENABLED=false` y no publicar el puerto 8080.
