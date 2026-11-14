# Evidencia de pruebas del backend

**Responsable:** Persona 1 (backend y despliegue)
**Última ejecución:** 13/11/2026
**Comando:** `./mvnw test` (Windows: `mvnw.cmd test`)

---

## 1. Resultado global

```text
Tests run: 19, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

| Clase | Pruebas | Cobertura funcional |
| --- | ---: | --- |
| `GroupingEngineTest` | 7 | Reglas C1, C2, C3/C4, C5, similitud baja, embeddings caídos |
| `ReportFlowIntegrationTest` | 7 | Flujo completo, C2, C3, C4, C5, validaciones, roles y salud |
| `HttpDescriptionSimilarityClientTest` | 4 | Contrato HTTP, campos snake_case, valores fuera de rango y enfriamiento |
| `SchemaValidationTest` | 1 | Flyway + mapeo JPA con `ddl-auto=validate` |

---

## 2. Casos del informe

| Caso | Prueba o evidencia | Resultado |
| --- | --- | --- |
| C1 — misma falla cercana | `c1GroupsSameSectorTimeAndType`, flujo completo | Propone agrupación, certeza `ALTA` |
| C2 — mismo sector/tiempo, tipo distinto | `c2DifferentTypeIsLowCertainty`, `c2LowCertainty` | Vincula con `BAJA` y `requiresReview=true` |
| C3 — fuera de la ventana | `c3OutsideWindow` | Crea incidencia nueva |
| C4 — sector distinto | `c3c4NoCandidatesCreatesNewIncident`, `c4DifferentSectors` | No agrupa |
| C5 — ubicación incompleta | `c5UnknownSectorIsLowCertainty`, `c5MissingSector` | Caso `BAJA`, revisión explícita |
| C6 — falsas coincidencias | Flujo completo: separar y reasignar | Conserva UUID e historial |
| C7 — falla persiste | Flujo completo: `/persistence` | `REABIERTA` sin perder historial |

---

## 3. Pruebas de integración destacadas

`ReportFlowIntegrationTest.fullIncidentFlow` verifica, en orden:

1. Alta del primer reporte → incidencia `SUGERIDA`.
2. Alta del segundo reporte del mismo sector → mismo `incidentId`, certeza `ALTA`.
3. Acceso sin rol de operador → `403`.
4. Bandeja del operador → contiene el caso.
5. Confirmación → incidencia `CONFIRMADA`, reportes `CONFIRMADO`, historial.
6. Vista del técnico → 2 reportes y alcance estimado.
7. Técnico: `EN_ATENCION` → `RESUELTA`.
8. Cliente indica persistencia → `REABIERTA`.
9. Separación con caso nuevo → reporte con UUID intacto.
10. Reasignación al caso original → contador actualizado.

---

## 4. Validaciones cubiertas

- `reportedAt` en el futuro → `400 BAD_REQUEST`.
- Tipo de falla inexistente → `400 MALFORMED_JSON`.
- UUID inexistente → `404`.
- Transición de estado inválida → `409` (validado por la máquina de estados).
- Edición concurrente → `409 CONCURRENT_UPDATE` (bloqueo optimista).

---

## 5. Reproducción

```bash
cd backend
./mvnw test
```

Requisitos: JDK 21+. Las pruebas usan H2 en modo PostgreSQL y no requieren Docker ni conexión a internet después de descargar dependencias.

> Las pruebas no miden precisión/exhaustividad del motor sobre el dataset completo C1–C7: eso corresponde a Persona 3 con datos simulados. El endpoint `POST /api/v1/grouping/preview` está disponible para esa medición.
