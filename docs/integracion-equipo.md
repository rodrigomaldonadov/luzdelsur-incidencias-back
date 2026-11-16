# Integración entre personas — contratos y traspasos

Este documento define qué entrega Persona 1, qué debe completar cada rol y cómo evitar retrabajo. El backend ya está desplegable y probado; los puntos marcados como **pendiente** son responsabilidad de otra persona según el reparto del curso.

---

## 1. Matriz de responsabilidades

| Área | Persona 1 (esta entrega) | Persona 3 | Persona 5 | Personas 2 y 4 |
| --- | --- | --- | --- | --- |
| API REST | Implementada y documentada | Extiende estados/auditoría | — | Consume |
| Motor heurístico | Implementado y probado | Aporta dataset C1–C7 | — | — |
| PostgreSQL | Migraciones `V1` y `V2` operativas | `V3+`: usuarios, roles, auditoría | — | — |
| Seguridad | HTTPS, red interna, roles temporales y propiedad de reportes | JWT real, mínimo privilegio, auditoría | — | Login/registro y manejo de sesión |
| Embeddings | Cliente HTTP tolerante a fallos + mock | — | Servicio FastAPI real | — |
| Docker/despliegue | Compose, Dockerfile, Nginx, scripts | — | Dockerfile del microservicio | — |
| Pruebas | Motor y flujo de integración | Pruebas de datos | Pruebas del servicio | Pruebas funcionales de vistas |
| Documentación | Arquitectura y justificación | Datos y resultados del motor | READMEs e informe final | Flujo de demo, ética y escalabilidad |

### 1.1 Completado en esta iteración (Persona 1)

| Endpoint / cambio | Estado | Nota |
| --- | --- | --- |
| `GET /api/v1/reports/mine` | Implementado | Exige identidad; devuelve solo los reportes del actor |
| `GET /api/v1/technician/incidents` | Implementado | Casos `CONFIRMADA`, `EN_ATENCION` o `REABIERTA`; filtro `onlyMine` |
| `GET /api/v1/technician/incidents/{id}` ampliado | Implementado | Asignación, marcas de estado y coordenadas de los reportes |
| Migración `V2__technician_assignment.sql` | Implementada | `assigned_technician_id` y `assigned_at` |

### 1.2 Completado en la rama `develop` (autenticación, auditoría e integración)

| Endpoint / cambio | Estado | Nota |
| --- | --- | --- |
| `POST /api/v1/auth/login` | Implementado | JWT HS256 + BCrypt; valida el rol si el formulario lo envía |
| `POST /api/v1/auth/register` | Implementado | Solo `CLIENTE` salvo `APP_AUTH_ALLOW_STAFF_REGISTRATION=true` |
| `GET /api/v1/auth/me` | Implementado | Relee la cuenta (rechaza cuentas desactivadas) |
| `POST /api/v1/auth/refresh` | Implementado | Token de refresco de 7 días |
| `JwtActorProvider` (`@Primary`) | Implementado | `sub` del token = `Actor.id`; cabeceras `X-Actor-*` solo con `APP_ALLOW_DEV_HEADERS=true` |
| `V3__users_and_roles.sql` | Implementada | Tabla `app_user` |
| `V4__audit_and_status_history.sql` | Implementada | `audit_event` e `incident_status_history` |
| `V5__report_feedback.sql` | Implementada | Calificación del cliente e índice por `client_id` |
| `GET /api/v1/technician/incidents/history` y `/{id}/timeline` | Implementados | Historial del técnico sin pasar por endpoints de operador |
| `POST /api/v1/operator/incidents/{id}/assign` | Implementado | Asignación manual; un técnico no puede tomar el caso de otro (`409`) |
| `GET/POST/PATCH /api/v1/operator/technicians` | Implementados | Gestión de técnicos |
| `GET /api/v1/operator/metrics` | Implementado | Panel de monitoreo |
| `GET /api/v1/operator/audit-events` | Implementado | Auditoría transversal |
| `POST /api/v1/reports/{id}/feedback` | Implementado | Calificación de la atención |
| Semilla de demo (`DemoDataSeeder`) | Implementada | 4 cuentas + dataset C1–C5 y casos en cada estado |

---

## 2. Traspaso a Persona 3 — base de datos, JWT y auditoría

### 2.1 No modificar la migración aplicada

`V1__baseline.sql` y `V2__technician_assignment.sql` ya están desplegadas. Agregar nuevas migraciones:

```text
backend/src/main/resources/db/migration/
├── V1__baseline.sql              (Persona 1, no editar)
├── V2__technician_assignment.sql (Persona 1, no editar)
├── V3__users_and_roles.sql       (Persona 3)
└── V4__audit_events.sql          (Persona 3)
```

### 2.2 Autenticación JWT

El backend expone un punto de extensión limpio:

```java
public interface ActorProvider {
    Actor current();   // Actor(id, ActorRole)
}
```

`HeaderActorProvider` es temporal. Persona 3 debe:

1. Agregar `spring-boot-starter-security` y el filtro JWT.
2. Crear `JwtActorProvider implements ActorProvider` con `@Primary`, leyendo `sub` y `role` del token. El `sub` debe usarse como `Actor.id` porque de él depende la propiedad de `/api/v1/reports/mine`.
3. Configurar `SecurityFilterChain`:
   - `/api/v1/reports` (POST) → público o `CLIENTE`, según decisión del equipo.
   - `/api/v1/reports/mine` → `CLIENTE` autenticado (nunca anónimo).
   - `/api/v1/reports/{id}` → público en el prototipo; evaluar restricción por dueño en una iteración posterior.
   - `/api/v1/operator/**` → `hasRole('OPERADOR')`.
   - `/api/v1/technician/**` → `hasRole('TECNICO')`.
   - `/api/v1/grouping/preview` → `OPERADOR`.
   - `/actuator/health/**` → público; resto de Actuator, bloqueado.
4. Retirar `app.security.dev-default-role` y las cabeceras `X-Actor-*` del frontend.

**Importante:** los controladores ya llaman `accessControl.require(...)`. Al reemplazar el proveedor, la autorización por rol sigue funcionando sin tocar servicios ni DTOs. Si además se activa Spring Security, ambos controles conviven; se recomienda dejar `AccessControl` como verificación de dominio y el filtro como autenticación.

### 2.3 Auditoría

`report_link_history` ya registra:

- `VINCULADO`, `CONFIRMADO`, `SEPARADO`, `REASIGNADO`, `REABIERTO`
- actor, notas, incidencia origen/destino y fecha

Persona 3 debe agregar la auditoría transversal (login, consultas, cambios de estado) en `audit_event` y exponer los endpoints de consulta. No es necesario duplicar el historial de vínculos.

### 2.4 Datos simulados C1–C7

El motor y los filtros ya están listos. Para cargar el dataset:

- Preferir `POST /api/v1/reports` con `reportedAt` explícito (permite C3 sin esperar).
- Para pruebas sin persistir, usar `POST /api/v1/grouping/preview`.
- Persona 3 puede crear un `CommandLineRunner` con `@Profile("demo")` o un script SQL; **no** incluir datos de demostración en `V1`/`V2`.
- Mantener 15–20 descripciones variadas: sinónimos (“no hay luz”, “corte de energía”, “se fue la electricidad”), ruido (“el medidor hace ruido”), ubicaciones incompletas y tipos distintos.

Mapeo rápido:

| Caso | Cómo generarlo por API |
| --- | --- |
| C1 | Dos reportes mismo sector/tipo dentro de `GROUPING_WINDOW_MINUTES` |
| C2 | Mismo sector y ventana, `failureType` distinto |
| C3 | `reportedAt` separado más que la ventana |
| C4 | Sectores distintos, mismo tipo y hora |
| C5 | Omitir `sector` o enviar `"sin sector"` |
| C6 | Dos fallas reales en el mismo sector/hora; separar y reasignar |
| C7 | Resolver un caso y luego `POST /reports/{id}/persistence` |

### 2.5 Estados y notificaciones

La máquina de estados está en `IncidentStatus`. Si Persona 3 requiere nuevos estados (por ejemplo `ASIGNADA`), debe:

1. Agregar el enum y sus transiciones.
2. Crear la migración si cambia el esquema (el estado se guarda como texto).
3. Actualizar `ApiMapper.clientMessageForStatus`.
4. Añadir pruebas de transición.

Las notificaciones al cliente hoy son el mensaje de consulta (`clientMessage`). Notificaciones push/email están fuera del alcance.

---

## 3. Traspaso a Persona 5 — microservicio de embeddings

### 3.1 Contrato obligatorio

| Método | Ruta | Cuerpo | Respuesta |
| --- | --- | --- | --- |
| `POST` | `/api/v1/similarity` | `{"text_a": "...", "text_b": "..."}` | `{"similarity": 0.87}` |
| `POST` | `/api/v1/similarity/batch` | `{"text": "...", "candidates": ["...", "..."]}` | `{"similarities": [0.87, 0.12]}` |
| `GET` | `/health` | — | `{"status": "ok"}` |

Requisitos:

- Similitud en `[0, 1]`; textos vacíos → `0.0`.
- Responder en menos de 1,5 s para textos cortos (el backend corta a ese tiempo).
- Soportar concurrencia básica; cargar el modelo una sola vez al iniciar.
- Nombres de campos exactos (snake_case en similitud simple).
- Sin autenticación en la red interna de Docker (el backend es el único consumidor).

### 3.2 Docker

```dockerfile
FROM python:3.12-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY app ./app
EXPOSE 8000
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
```

El `docker-compose.yml` ya declara el servicio `embeddings`, con contexto configurable:

```env
EMBEDDINGS_CONTEXT=../embeddings
EMBEDDINGS_IMAGE=luzdelsur/embeddings:latest
EMBEDDINGS_MODEL=sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2
```

Si el modelo se descarga al iniciar, la primera petición puede tardar más de 1,5 s. Se recomienda:

- Incluir el modelo en la imagen o en un volumen.
- Precalentar con un texto al arrancar.
- Exponer `/health` cuando el modelo esté listo (el backend tolera fallos, pero la demo se ve mejor).

### 3.3 Prueba de integración

```bash
curl -X POST http://localhost:8000/api/v1/similarity/batch \
  -H "Content-Type: application/json" \
  -d '{"text":"no hay luz en la cuadra","candidates":["corte de energia en la zona","falla del medidor"]}'
```

Luego, con el stack levantado:

```bash
curl https://TU_DOMINIO/api/v1/grouping/config
# embeddingsStatus debe ser "DISPONIBLE"
```

### 3.4 Mock temporal

`deploy/mock-embeddings` implementa el contrato con similitud léxica (Jaccard) y existe solo para desarrollo. No debe presentarse como el entregable de Persona 5.

---

## 4. Traspaso al frontend (Personas 2 y 4)

### 4.1 Configuración

```env
VITE_API_BASE_URL=https://TU_DOMINIO
```

Mientras no exista JWT:

```http
X-Actor-Id: operador-1
X-Actor-Role: OPERADOR
```

CORS se controla con `APP_CORS_ALLOWED_ORIGINS` (incluir el origen exacto del frontend, con esquema y puerto).

### 4.2 Flujos mínimos

| Vista | Endpoints | Estados de UI |
| --- | --- | --- |
| Cliente: registrar | `POST /api/v1/reports` | Enviando, éxito con UUID, error de validación |
| Cliente: seguimiento | `GET /api/v1/reports/{id}` | `SUGERIDA`, `CONFIRMADA`, `EN_ATENCION`, `RESUELTA`, `CERRADA`, `REABIERTA` |
| Cliente: mis reportes | `GET /api/v1/reports/mine` | Paginado; requiere identidad y muestra estado por reporte |
| Cliente: persistencia | `POST /api/v1/reports/{id}/persistence` | Confirmación y mensaje de reapertura |
| Operador: bandeja | `GET /api/v1/operator/incidents` | Etiqueta **Baja certeza**, prioridad, contador de reportes |
| Operador: detalle | `GET /api/v1/operator/incidents/{id}` | Reportes de origen, historial, alertas |
| Operador: acciones | `confirm`, `separate`, `reassign`, `status` | Confirmación previa, error `409` = recargar |
| Técnico | `GET /api/v1/technician/incidents` y `/{id}` | Bandeja por estado, filtro `onlyMine`, asignación y coordenadas |
| Login/registro | `POST /api/v1/auth/login`, `/register`, `/refresh`, `GET /me` | Error de credenciales (`401`), rol equivocado (`403`), sesión vencida |
| Técnico: historial | `GET /api/v1/technician/incidents/history` y `/{id}/timeline` | Notas de resolución en `statusHistory` |
| Cliente: calificación | `POST /api/v1/reports/{id}/feedback` | Visible si `canRate=true` |

### 4.3 Reglas de presentación

- Mostrar `clientMessage` tal cual; está redactado para no prometer resolución.
- No ocultar `requiresReview` ni `alerts`.
- Diferenciar `linkStatus`: `SUGERIDO` (propuesta), `CONFIRMADO` (validado), `SEPARADO`, `REASIGNADO`.
- En `409`, mostrar “El caso fue modificado por otro operador” y recargar.
- En `429`, espaciar solicitudes (límite de Nginx).
- El cliente no debe ver IDs de operadores ni notas internas.

### 4.4 Demo

Los casos C1–C7 y el flujo completo ya se pueden reproducir con `curl` o Swagger (`SPRINGDOC_ENABLED=true`). Coordinar con Persona 4 el guion y las capturas.

---

## 5. Checklist de integración antes de la demo

- [ ] `docker compose ps` con todos los servicios arriba.
- [ ] `GET /health` responde `UP`.
- [ ] `GET /api/v1/grouping/config` muestra `embeddingsStatus` esperado.
- [ ] Alta de reporte desde el frontend (CORS correcto).
- [ ] Dos reportes del mismo sector se agrupan y muestran la etiqueta de certeza.
- [ ] Operador confirma; el cliente ve el nuevo estado.
- [ ] Técnico consulta el contexto del caso confirmado.
- [ ] Separar y reasignar conserva el UUID original.
- [ ] Caso C7 reabre sin perder historial.
- [ ] Errores `400/403/404/409` muestran mensajes claros en la UI.
- [ ] Respaldos y certificados verificados.
- [ ] `APP_JWT_SECRET` y `APP_DEMO_PASSWORD` definidos en `.env`; `APP_ALLOW_DEV_HEADERS=false`.

---

## 6. Pendientes heredados (no bloquean la entrega de Persona 1)

| Pendiente | Responsable | Impacto |
| --- | --- | --- |
| Dataset C1–C7 ampliado a 15–20 descripciones | Persona 3 | La semilla cubre C1–C5 con 15 reportes; C6 y C7 se demuestran en vivo |
| Microservicio real de embeddings | Persona 5 | Mock para demo |
| Vistas completas y QA | Personas 2 y 4 | API lista |
| Pruebas de carga | Fuera de alcance | Capacidad desconocida |
| Notificaciones push/email | Fuera de alcance | Solo mensajes en pantalla |

---

## 7. Contacto técnico

- Código del backend: `backend/`
- Contrato HTTP: [`api.md`](api.md)
- Arquitectura y modelo: [`arquitectura.md`](arquitectura.md)
- Justificación: [`justificacion-backend.md`](justificacion-backend.md)
- Despliegue: [`despliegue.md`](despliegue.md)
