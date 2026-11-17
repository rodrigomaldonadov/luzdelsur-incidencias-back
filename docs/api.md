# Contrato de API REST — v1

**Base local:** `http://localhost:8080`
**Base desplegada:** `https://<DOMINIO>` (Nginx → backend)
**Formato:** JSON UTF-8. Fechas en ISO-8601 UTC (`2026-11-13T15:30:00Z`).
**Documentación interactiva:** `GET /docs` (Swagger UI) y `GET /v3/api-docs`. En producción llega deshabilitada si `SPRINGDOC_ENABLED=false`.

---

## 1. Autenticacion e identidad (JWT)

La identidad sale del token: `Authorization: Bearer <token>`. El `sub` del token es el UUID del usuario y es el valor que el backend guarda como dueño del reporte, actor del historial y técnico asignado.

| Método | Ruta | Acceso | Descripción |
| --- | --- | --- | --- |
| `POST` | `/api/v1/auth/register` | Público | Crea una cuenta (por defecto `CLIENTE`) y devuelve la sesión |
| `POST` | `/api/v1/auth/login` | Público | Usuario + contraseña; si se envía `role` y no coincide, `403` |
| `POST` | `/api/v1/auth/refresh` | Público | Renueva el token de acceso con el `refreshToken` |
| `GET` | `/api/v1/auth/me` | Con token | Usuario de la sesión actual |

`POST /api/v1/auth/login`

```json
{ "username": "operador@luzdelsur.com.pe", "password": "********", "role": "OPERADOR" }
```

Respuesta `200` (el registro responde lo mismo con `201`):

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "expiresAt": "2026-11-16T18:00:00Z",
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshExpiresAt": "2026-11-23T17:00:00Z",
  "user": {
    "id": "6c1f2f0e-3d1b-4a51-9a52-4f3c1c0f8a10",
    "name": "Mesa de Control Central",
    "username": "operador@luzdelsur.com.pe",
    "role": "OPERADOR"
  }
}
```

Reglas:

- El usuario se normaliza (minúsculas, sin espacios). Contraseña de 6 a 72 caracteres, guardada con BCrypt.
- Login fallido responde `401` con el mismo mensaje para usuario inexistente o contraseña incorrecta.
- El registro público solo crea `CLIENTE`. Las cuentas de técnico las crea un operador (`POST /api/v1/operator/technicians`) o la semilla de demo. Para permitir el autoregistro de personal: `APP_AUTH_ALLOW_STAFF_REGISTRATION=true`.
- Token de acceso: 60 min (`APP_JWT_ACCESS_TTL_MINUTES`). Refresco: 7 días (`APP_JWT_REFRESH_TTL_DAYS`). Al refrescar se relee el rol y el estado de la cuenta.
- Un token inválido o vencido responde `401 UNAUTHORIZED`; el frontend debe intentar `refresh` una vez y, si falla, cerrar la sesión.
- Sin token, la petición es anónima con rol `CLIENTE`: solo alcanza para el alta y el seguimiento por UUID.

### Cuentas de demostración

Con `APP_DEMO_SEED_USERS=true` (por defecto) se crean, si no existen:

| Usuario | Rol |
| --- | --- |
| `operador@luzdelsur.com.pe` | `OPERADOR` |
| `tecnico@luzdelsur.com.pe` | `TECNICO` |
| `tecnico2@luzdelsur.com.pe` | `TECNICO` |
| `cliente@luzdelsur.com.pe` | `CLIENTE` |

Contraseña: `APP_DEMO_PASSWORD` (en local, `12345678`). Con `APP_DEMO_SEED_REPORTS=true` y la tabla de reportes vacía se carga además un dataset que reproduce C1–C5 y deja un caso confirmado y asignado, uno en atención y uno resuelto.

### Cabeceras de desarrollo

`X-Actor-Id` y `X-Actor-Role` solo se aceptan si `APP_ALLOW_DEV_HEADERS=true` y la petición no trae token. Sirven para pruebas locales; en despliegue quedan desactivadas y no otorgan permisos.

### Regla de propiedad de los reportes

Al crear un reporte con sesión, el servidor guarda el `sub` del token como dueño y descarta cualquier `clientId` enviado en el cuerpo. Así se evita que un cliente liste reportes ajenos cambiando el payload. `GET /api/v1/reports/mine` exige sesión y devuelve únicamente los reportes de ese usuario.

### Errores uniformes

```json
{
  "timestamp": "2026-11-13T15:30:00Z",
  "status": 409,
  "code": "CONFLICT",
  "message": "Transicion no permitida: CONFIRMADA -> CERRADA",
  "details": [],
  "path": "/api/v1/operator/incidents/8f0c.../status"
}
```

| Código HTTP | `code` | Cuándo |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR`, `BAD_REQUEST`, `MALFORMED_JSON`, `INVALID_PARAMETER` | Datos inválidos o enum inexistente |
| 401 | `UNAUTHORIZED` | Sin sesión, credenciales incorrectas, token inválido o vencido |
| 403 | `FORBIDDEN` | Rol insuficiente, cuenta desactivada o recurso ajeno |
| 404 | `NOT_FOUND` | UUID inexistente |
| 409 | `CONFLICT`, `CONCURRENT_UPDATE` | Transición inválida, caso cerrado, edición concurrente |
| 500 | `INTERNAL_ERROR` | Error no controlado (detalle solo en logs) |

---

## 2. Resumen de endpoints

| Método | Ruta | Rol | Descripción |
| --- | --- | --- | --- |
| `POST` | `/api/v1/reports` | Público | Registra reporte, ejecuta el motor y devuelve UUID + mensaje |
| `GET` | `/api/v1/reports/{reportId}` | Público | Estado comprensible del reporte |
| `POST` | `/api/v1/auth/register` · `/login` · `/refresh` | Público | Sesión JWT (ver sección 1) |
| `GET` | `/api/v1/auth/me` | Con token | Usuario de la sesión |
| `GET` | `/api/v1/reports/mine` | Con token | Reportes propios paginados |
| `POST` | `/api/v1/reports/{reportId}/persistence` | Público | Caso C7: el cliente indica que la falla persiste |
| `POST` | `/api/v1/reports/{reportId}/feedback` | Dueño del reporte | Calificación 1–5 con el caso resuelto o cerrado |
| `GET` | `/api/v1/operator/incidents` | `OPERADOR` | Bandeja priorizada y filtrable |
| `GET` | `/api/v1/operator/incidents/{id}` | `OPERADOR` | Detalle + reportes + historial + alertas |
| `POST` | `/api/v1/operator/incidents/{id}/confirm` | `OPERADOR` | Confirma la agrupación sugerida |
| `POST` | `/api/v1/operator/incidents/{id}/status` | `OPERADOR` | Cambia estado (máquina de estados) |
| `POST` | `/api/v1/operator/reports/{reportId}/separate` | `OPERADOR` | Separa un reporte (opcionalmente crea caso nuevo) |
| `POST` | `/api/v1/operator/reports/{reportId}/reassign` | `OPERADOR` | Reasigna a otra incidencia abierta |
| `GET` | `/api/v1/operator/reports` | `OPERADOR` | Bandeja individual (alternativa A, pruebas H4) |
| `POST` | `/api/v1/operator/incidents/{id}/assign` | `OPERADOR` | Asigna el caso a un técnico |
| `GET` | `/api/v1/operator/metrics` | `OPERADOR` | Indicadores del panel de monitoreo |
| `GET` · `POST` | `/api/v1/operator/technicians` | `OPERADOR` | Directorio de técnicos con su carga; alta de técnico |
| `PATCH` | `/api/v1/operator/technicians/{id}` | `OPERADOR` | Activa o desactiva a un técnico |
| `GET` | `/api/v1/operator/audit-events` | `OPERADOR` | Auditoría de accesos y acciones |
| `GET` | `/api/v1/technician/incidents` | `TECNICO` | Bandeja de casos confirmados/en atención |
| `GET` | `/api/v1/technician/incidents/{id}` | `TECNICO` | Contexto consolidado del caso confirmado |
| `GET` | `/api/v1/technician/incidents/history` | `TECNICO` | Tareas cumplidas (`RESUELTA`/`CERRADA`), filtro `onlyMine` |
| `GET` | `/api/v1/technician/incidents/{id}/timeline` | `TECNICO` | Historial del caso con notas de estado |
| `POST` | `/api/v1/technician/incidents/{id}/status` | `TECNICO` | `EN_ATENCION` o `RESUELTA` |
| `POST` | `/api/v1/grouping/preview` | `OPERADOR` | Evalúa reglas sin persistir (QA / H3) |
| `GET` | `/api/v1/grouping/config` | Público | Ventana, umbrales y estado de embeddings |
| `GET` | `/actuator/health` | Interno | Salud (Nginx expone solo `/health`) |

---

## 3. Alta de reporte

`POST /api/v1/reports`

```json
{
  "clientId": "cliente-001",
  "sector": "San Isidro",
  "reference": "Av. Arequipa 1234",
  "failureType": "CORTE_TOTAL",
  "description": "No hay luz en toda la cuadra desde hace 20 minutos",
  "latitude": -12.0975,
  "longitude": -77.0365,
  "reportedAt": "2026-11-13T15:30:00Z"
}
```

| Campo | Obligatorio | Regla |
| --- | --- | --- |
| `failureType` | Sí | `CORTE_TOTAL`, `BAJA_TENSION`, `INTERMITENCIA`, `MEDIDOR`, `ALUMBRADO_PUBLICO`, `OTRO` |
| `description` | Sí | 1–1000 caracteres |
| `sector` | No | 0–120; si falta o es ambiguo se aplica C5 (`BAJA`) |
| `reportedAt` | No | Por defecto `now`; no puede superar 5 minutos en el futuro |
| `latitude` / `longitude` | No | Rangos geográficos válidos |

> Si la petición incluye identidad (`X-Actor-Id` o JWT), el backend usa ese identificador como dueño del reporte y **ignora** `clientId` del cuerpo. Es la regla que protege `GET /api/v1/reports/mine`.

**Respuesta `201 Created`**

```json
{
  "report": {
    "id": "0dfb1043-00ad-47db-bca4-8792d28ebc82",
    "sector": "San Isidro",
    "failureType": "CORTE_TOTAL",
    "description": "No hay luz en toda la cuadra desde hace 20 minutos",
    "reportedAt": "2026-11-13T15:30:00Z",
    "incidentId": "0fc62486-9711-4e74-9a3d-cb31df01e282",
    "linkStatus": "SUGERIDO",
    "certainty": "ALTA",
    "groupingReason": "Sector coincidente: San Isidro; Dentro de la ventana de 30 minutos; Tipo de falla coincidente: CORTE_TOTAL",
    "linkUpdatedAt": "2026-11-13T15:57:59Z"
  },
  "incidentId": "0fc62486-9711-4e74-9a3d-cb31df01e282",
  "incidentStatus": "SUGERIDA",
  "certainty": "ALTA",
  "requiresReview": false,
  "clientMessage": "Tu reporte se vinculo a una incidencia existente en tu sector. Un operador confirmara la agrupacion.",
  "reasons": [
    "Sector coincidente: San Isidro",
    "Dentro de la ventana de 30 minutos",
    "Tipo de falla coincidente: CORTE_TOTAL"
  ]
}
```

`certainty` puede ser `ALTA`, `MEDIA` o `BAJA`. Un caso `BAJA` devuelve `requiresReview: true` y un mensaje que no promete resolución.

---

## 4. Seguimiento del cliente

`GET /api/v1/reports/{reportId}`

```json
{
  "report": { "id": "0dfb...", "linkStatus": "CONFIRMADO", "certainty": "ALTA", "..." : "..." },
  "incidentId": "0fc6...",
  "incidentStatus": "EN_ATENCION",
  "certainty": "ALTA",
  "requiresReview": false,
  "incidentUpdatedAt": "2026-11-13T16:10:00Z",
  "clientMessage": "Se esta atendiendo la incidencia vinculada a tu reporte."
}
```

El seguimiento no expone notas del operador, identificadores de trabajadores ni datos de otros clientes.

### Mis reportes

`GET /api/v1/reports/mine?page=0&size=20`

Requiere sesión (JWT). Devuelve `PageResponse<TrackingResponse>` ordenado por `reportedAt` descendente:

```json
{
  "content": [
    {
      "report": { "id": "0dfb...", "incidentId": "0fc6...", "linkStatus": "SUGERIDO" },
      "incidentStatus": "SUGERIDA",
      "certainty": "ALTA",
      "requiresReview": false,
      "incidentUpdatedAt": "2026-11-16T10:00:00Z",
      "clientMessage": "Tu reporte esta vinculado a una posible incidencia en tu sector..."
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

Sin sesión responde `403 FORBIDDEN`. El servidor asocia el reporte al usuario autenticado: no se puede consultar cambiando el `clientId` del cuerpo.

Cada `TrackingResponse` incluye además `canRate` (el caso está resuelto o cerrado y aún no tiene calificación) y `feedback` (`rating`, `comment`, `createdAt`) cuando existe.

### Calificar la atención

`POST /api/v1/reports/{reportId}/feedback`

```json
{ "rating": 5, "comment": "Volvió la luz en menos de una hora" }
```

- Solo el dueño del reporte (`403` para otros), una vez (`409` si ya calificó) y con el caso `RESUELTA` o `CERRADA` (`409` en otro estado).
- Si la falla persiste, el camino es C7 (`/persistence`), no la calificación.

### C7 — la falla persiste

`POST /api/v1/reports/{reportId}/persistence`

```json
{ "note": "La luz sigue sin volver" }
```

- Si el caso estaba `RESUELTA` o `CERRADA`, pasa a `REABIERTA` y se registra el evento.
- Si sigue abierto, responde sin cambiar el estado.
- Si estaba `DESCARTADA`, responde `409`.

---

## 5. Bandeja del operador

`GET /api/v1/operator/incidents?status=SUGERIDA&requiresReview=true&sector=San%20Isidro&page=0&size=20`

Parámetros: `status`, `certainty`, `requiresReview`, `failureType`, `sector`, `onlyOpen` (default `true`), `page`, `size` (máx. 50).

Orden: `priorityScore` descendente y `firstReportedAt` ascendente (lo más antiguo primero dentro de la misma prioridad).

```json
{
  "content": [
    {
      "id": "0fc6...",
      "sector": "San Isidro",
      "failureType": "CORTE_TOTAL",
      "status": "SUGERIDA",
      "certainty": "ALTA",
      "requiresReview": false,
      "priorityScore": 38.83,
      "priorityLabel": "MEDIA",
      "reportCount": 2,
      "firstReportedAt": "2026-11-13T15:47:58Z",
      "lastReportedAt": "2026-11-13T15:49:58Z",
      "updatedAt": "2026-11-13T15:57:59Z",
      "version": 2
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

### Detalle

`GET /api/v1/operator/incidents/{id}` devuelve `incident`, `reports[]`, `history[]` y `alerts[]`. Las alertas se generan automáticamente: baja certeza, sectores incompletos, tipos de falla mezclados, caso reabierto, agrupación pendiente o caso descartado.

### Confirmar (T1)

`POST /api/v1/operator/incidents/{id}/confirm`

```json
{ "note": "Agrupacion verificada con el cliente" }
```

Solo desde `SUGERIDA`. Pasa la incidencia a `CONFIRMADA`, los reportes a `CONFIRMADO` y registra historial.

### Cambiar estado

`POST /api/v1/operator/incidents/{id}/status`

```json
{ "status": "EN_ATENCION", "note": "Cuadrilla asignada" }
```

Transiciones válidas en [`arquitectura.md`](arquitectura.md#8-máquina-de-estados). Cualquier otra devuelve `409`.

### Separar (T2)

`POST /api/v1/operator/reports/{reportId}/separate`

```json
{ "createNewIncident": true, "note": "El reporte corresponde a otra falla" }
```

Respuesta:

```json
{
  "sourceIncident": { "incident": { "reportCount": 1 }, "reports": [ "..." ] },
  "newIncidentId": "8a31..."
}
```

- `createNewIncident` por defecto es `true`; el reporte se mueve a un caso nuevo en estado `SUGERIDA`.
- Si el caso de origen queda sin reportes, pasa a `DESCARTADA` (no se borra).
- El UUID del reporte nunca cambia.

### Reasignar (T3)

`POST /api/v1/operator/reports/{reportId}/reassign`

```json
{ "targetIncidentId": "0fc62486-9711-4e74-9a3d-cb31df01e282", "note": "Reintegrado" }
```

Solo a casos abiertos. La certeza resultante es `MEDIA` si coinciden sector y tipo; `BAJA` en caso contrario (requiere revisión).

### Bandeja individual (alternativa A)

`GET /api/v1/operator/reports?linkStatus=SUGERIDO&from=2026-11-13T00:00:00Z&page=0&size=20`

Sirve para comparar tiempos de revisión individual vs. agrupada (H4).

---

## 6. Técnico de campo

### Bandeja del técnico

`GET /api/v1/technician/incidents?status=EN_ATENCION&sector=Lince&onlyMine=true&page=0&size=20`

- Solo devuelve casos `CONFIRMADA`, `EN_ATENCION` o `REABIERTA`; pedir `SUGERIDA` responde `400`.
- `onlyMine=true` filtra por el técnico autenticado (`X-Actor-Id` hoy, JWT después) y exige identidad.
- Orden: prioridad descendente y antigüedad ascendente.

```json
{
  "content": [
    {
      "id": "0fc6...",
      "sector": "Lince",
      "failureType": "CORTE_TOTAL",
      "status": "EN_ATENCION",
      "certainty": "ALTA",
      "requiresReview": false,
      "priorityLabel": "MEDIA",
      "priorityScore": 42.1,
      "affectedReports": 2,
      "firstReportedAt": "2026-11-16T15:47:58Z",
      "lastReportedAt": "2026-11-16T15:49:58Z",
      "assignedTechnicianId": "tec-lince",
      "assignedAt": "2026-11-16T16:02:00Z",
      "estimatedScope": "Sector Lince - 2 reporte(s) entre 16/11 10:47 y 16/11 10:49"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

Al pasar el caso a `EN_ATENCION`, el backend registra `assignedTechnicianId` y `assignedAt` con el actor que inició la atención. No existe despacho automático de flotas ni asignación geográfica.

### Detalle ampliado

`GET /api/v1/technician/incidents/{id}`

```json
{
  "id": "0fc6...",
  "sector": "San Isidro",
  "failureType": "CORTE_TOTAL",
  "status": "CONFIRMADA",
  "certainty": "ALTA",
  "requiresReview": false,
  "priorityLabel": "MEDIA",
  "priorityScore": 38.83,
  "affectedReports": 2,
  "firstReportedAt": "2026-11-13T15:47:58Z",
  "lastReportedAt": "2026-11-13T15:49:58Z",
  "confirmedAt": "2026-11-13T16:00:00Z",
  "confirmedBy": "op-1",
  "statusUpdatedAt": "2026-11-13T16:05:00Z",
  "statusUpdatedBy": "tec-1",
  "assignedTechnicianId": "tec-1",
  "assignedAt": "2026-11-13T16:05:00Z",
  "closedAt": null,
  "updatedAt": "2026-11-13T16:05:00Z",
  "estimatedScope": "Sector San Isidro - 2 reporte(s) entre 13/11 10:47 y 13/11 10:49",
  "reports": [
    {
      "id": "0dfb...",
      "sector": "San Isidro",
      "reference": "Av. Arequipa 1234",
      "description": "No hay luz en toda la cuadra",
      "reportedAt": "2026-11-13T15:49:58Z",
      "linkStatus": "CONFIRMADO",
      "certainty": "ALTA",
      "latitude": -12.0850,
      "longitude": -77.0350
    }
  ],
  "warnings": []
}
```

Un caso `SUGERIDA` o `DESCARTADA` devuelve `409`: el técnico solo ve casos confirmados por el operador.

`POST /api/v1/technician/incidents/{id}/status` acepta únicamente `EN_ATENCION` o `RESUELTA`. La `note` queda en `statusHistory`. Si el caso está asignado a otro técnico responde `409`; si no tiene técnico, queda asignado a quien inicia la atención.

### Tareas cumplidas

`GET /api/v1/technician/incidents/history?onlyMine=true&sector=&page=0&size=20`

Mismo formato que la bandeja, con los casos `RESUELTA` y `CERRADA`, del más reciente al más antiguo. Cada fila trae además `statusUpdatedAt`, `closedAt` y `updatedAt`.

### Historial del caso

`GET /api/v1/technician/incidents/{id}/timeline` devuelve el mismo `IncidentDetail` que el detalle del operador.

### Historial de estados (`statusHistory`)

Todo `IncidentDetail` (operador y técnico) incluye `statusHistory`, ordenado por fecha:

```json
{
  "id": 12,
  "eventType": "CAMBIO_ESTADO",
  "fromStatus": "EN_ATENCION",
  "toStatus": "RESUELTA",
  "actorId": "9b0e...",
  "actorName": "Cuadrilla T-08 (C. Huaman)",
  "actorRole": "TECNICO",
  "note": "Reemplazo de fusible de 20 A",
  "createdAt": "2026-11-16T17:20:00Z"
}
```

`eventType` es `CAMBIO_ESTADO` o `ASIGNACION`. Las entradas de `history` (vínculos de reportes) traen también `actorName` cuando el actor es una cuenta registrada.

### Asignación por el operador

`POST /api/v1/operator/incidents/{id}/assign`

```json
{ "technicianId": "9b0e...", "note": "Cuadrilla más cercana" }
```

Solo casos `CONFIRMADA`, `EN_ATENCION` o `REABIERTA`. El técnico debe existir, tener rol `TECNICO` y estar activo. Los candidatos salen de `GET /api/v1/operator/technicians?onlyActive=true`, que devuelve `id`, `name`, `username`, `active`, `openAssignments` y `lastLoginAt`.

### Panel de monitoreo

`GET /api/v1/operator/metrics` devuelve `openIncidents`, `pendingReview`, `lowCertaintyOpen`, `unassignedConfirmed`, `reportsLast24h`, `incidentsLast24h`, `avgMinutesToConfirm`, `avgMinutesToResolve` y los mapas `byStatus`, `openByPriority`, `openByFailureType`, más `topSectors` (`sector`, `openIncidents`, `reports`). Listo para los gráficos de Recharts.

### Auditoría

`GET /api/v1/operator/audit-events?action=LOGIN_FALLIDO&actorId=&entityId=&from=&to=&page=0&size=50`

Acciones: `REGISTRO`, `LOGIN_OK`, `LOGIN_FALLIDO`, `TOKEN_REFRESCADO`, `REPORTE_CREADO`, `PERSISTENCIA_REPORTADA`, `CALIFICACION_REGISTRADA`, `AGRUPACION_CONFIRMADA`, `ESTADO_CAMBIADO`, `REPORTE_SEPARADO`, `REPORTE_REASIGNADO`, `TECNICO_ASIGNADO`, `USUARIO_CREADO`, `USUARIO_ACTUALIZADO`. No se guardan contraseñas, tokens ni descripciones de reportes.

---

## 7. Motor de agrupación (QA / H3)

`GET /api/v1/grouping/config`

```json
{
  "windowMinutes": 30,
  "maxCandidates": 5,
  "embeddingsEnabled": true,
  "embeddingsAvailable": true,
  "embeddingsStatus": "DISPONIBLE",
  "matchThreshold": 0.55,
  "conflictThreshold": 0.30
}
```

`POST /api/v1/grouping/preview` (no persiste nada)

```json
{
  "sector": "San Isidro",
  "failureType": "CORTE_TOTAL",
  "description": "Sigue sin luz en la cuadra",
  "reportedAt": "2026-11-13T15:55:00Z"
}
```

```json
{
  "aggregate": true,
  "candidateIncidentId": "0fc6...",
  "certainty": "ALTA",
  "requiresReview": false,
  "sectorMatch": true,
  "timeMatch": true,
  "typeMatch": true,
  "similarity": 0.82,
  "reasons": [ "Sector coincidente: San Isidro", "..." ],
  "clientMessage": "..."
}
```

Uso recomendado para el informe: ejecutar el mismo conjunto con `GROUPING_WINDOW_MINUTES=15`, `30` y `60`, registrar `aggregate`/`candidateIncidentId` y comparar contra el resultado esperado de C1–C7 para calcular precisión y exhaustividad.

---

## 8. Correspondencia con los casos de prueba

| Caso | Petición | Resultado esperado |
| --- | --- | --- |
| C1 | Dos `POST /reports` mismo sector, tipo y ventana | Mismo `incidentId`, `certainty=ALTA` |
| C2 | Mismo sector/tiempo, distinto `failureType` | Mismo `incidentId`, `certainty=BAJA`, `requiresReview=true` |
| C3 | Mismo sector/tipo fuera de la ventana | `incidentId` distinto |
| C4 | Sectores distintos, mismo tipo/tiempo | `incidentId` distinto |
| C5 | Reporte sin `sector` | `certainty=BAJA`, `requiresReview=true` |
| C6 | Dos fallas que coinciden; operador separa/reasigna | Reporte con UUID intacto e historial |
| C7 | `POST /reports/{id}/persistence` sobre caso resuelto | `incidentStatus=REABIERTA` |

---

## 9. Ejemplos con `curl`

```bash
# Login (guarda el token en una variable)
TOKEN=$(curl -sk -X POST https://localhost/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"operador@luzdelsur.com.pe","password":"<APP_DEMO_PASSWORD>"}' | jq -r .token)

# Alta de reporte (anónima o con el token de un cliente)
curl -k -X POST https://localhost/api/v1/reports \
  -H "Content-Type: application/json" \
  -d '{"sector":"San Isidro","failureType":"CORTE_TOTAL","description":"No hay luz en la cuadra"}'

# Bandeja del operador
curl -k https://localhost/api/v1/operator/incidents -H "Authorization: Bearer $TOKEN"

# Confirmar agrupación
curl -k -X POST https://localhost/api/v1/operator/incidents/<ID>/confirm \
  -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" \
  -d '{"note":"Verificado"}'
```

En local sin TLS, cambiar `-k https://localhost` por `http://localhost:8080`.

---

## 10. Integración con el frontend (Personas 2 y 4)

- Configurar `VITE_API_URL` apuntando a `https://<dominio>/api`.
- Enviar siempre `Content-Type: application/json` y `Authorization: Bearer <token>` cuando hay sesión.
- Ante `401`, intentar `POST /api/v1/auth/refresh` una sola vez; si falla, cerrar la sesión.
- Tratar `409` como “recargar el caso”: otro operador lo modificó.
- Mostrar `certainty` y `requiresReview` con una etiqueta visible de **Baja certeza**; no ocultar `alerts`.
- El cliente debe mostrar `clientMessage` tal cual: está redactado para evitar promesas de resolución.
- Refrescar la bandeja cada 15–30 s durante la demo; no hay *websockets* en esta iteración.

---

## 11. Contrato con el microservicio de embeddings

El backend consume dos endpoints. Persona 5 debe respetar nombres y tipos:

### Similitud simple

`POST {EMBEDDINGS_BASE_URL}/api/v1/similarity`

```json
{ "text_a": "No hay luz en la cuadra", "text_b": "Corte de energia en la zona" }
```

```json
{ "similarity": 0.87 }
```

### Similitud por lote

`POST {EMBEDDINGS_BASE_URL}/api/v1/similarity/batch`

```json
{
  "text": "No hay luz en la cuadra",
  "candidates": ["Corte de energia en la zona", "Falla del medidor"]
}
```

```json
{ "similarities": [0.87, 0.12] }
```

Reglas de integración:

| Aspecto | Requisito |
| --- | --- |
| Rango | `0.0 ≤ similitud ≤ 1.0`; valores fuera de rango se descartan |
| Tiempo de respuesta | ≤ 1,5 s (`EMBEDDINGS_TIMEOUT_MS`) |
| Disponibilidad | El backend reintenta tras 30 s (`EMBEDDINGS_COOLDOWN_SECONDS`); no bloquea el alta |
| Salud | Se recomienda `GET /health` para el monitoreo |
| Idioma | Español; el modelo debe manejar paráfrasis |
| Vacíos | Textos vacíos deben devolver `0.0`, no error |

El mock de `deploy/mock-embeddings` implementa exactamente este contrato para demos sin modelo; **no** sustituye el entregable de Persona 5.

---

## 12. Notas de seguridad y privacidad

- Bajo la Ley 29733, las descripciones pueden contener datos personales: no se registran en logs de aplicación ni se devuelven a otros clientes.
- El endpoint público de alta tiene límite de tasa en Nginx; el cuerpo máximo es 1 MB.
- Nginx no expone `/actuator`; solo `/health` con el estado agregado.
- Contraseñas con BCrypt; tokens HS256 firmados con `APP_JWT_SECRET` (mínimo 32 caracteres, obligatorio en Compose).
- `/api/v1/auth/*` tiene en Nginx el mismo límite estricto que el alta de reportes (fuerza bruta).
- `APP_ALLOW_DEV_HEADERS` debe quedar en `false` en cualquier entorno accesible desde fuera.
