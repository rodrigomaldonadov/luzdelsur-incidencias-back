# Justificación del backend y de las decisiones técnicas

**Entregable:** Persona 1 — Backend + despliegue
**Proyecto:** Gestión y priorización de incidencias eléctricas — Luz del Sur (prototipo académico)

---

## 1. Qué debe resolver el backend

El punto 4 del Avance 2 define tres condiciones innegociables:

1. Probarse con reportes estructurados, sin acceso a APIs internas.
2. Mantener el control humano de la decisión final.
3. Conservar la trazabilidad inmutable de los reportes originales.

A partir de ellas, el backend debe: recibir reportes, **proponer** agrupaciones explicables, permitir confirmar/separar/reasignar, mantener estados consistentes, priorizar la bandeja y entregar contexto consolidado al técnico. La justificación que sigue evalúa las alternativas contra esos requisitos, no contra preferencias personales.

---

## 2. Por qué Spring Boot (Java 21)

| Criterio | Spring Boot | Node.js + Express | Django / FastAPI |
| --- | --- | --- | --- |
| Tipado y refactor seguro | Fuerte (Java 21, `record`, enums) | Débil salvo TS adicional | Medio (Python dinámico) |
| Transacciones y bloqueo optimista | Nativo con JPA (`@Transactional`, `@Version`) | Manual | Bueno en Django, básico en FastAPI |
| Validación declarativa | Bean Validation | Librerías externas | Pydantic (excelente) |
| Documentación de API | springdoc/OpenAPI | Swagger manual | OpenAPI nativo |
| Madurez para reglas de negocio | Alta | Media | Alta |
| Curva de aprendizaje del equipo | Media (usado en cursos) | Media | Baja |
| Ecosistema de contenedores | Imagen oficial + multi-stage | Muy bueno | Muy bueno |

**Decisión:** Spring Boot 3.5 + Java 21.

Razones concretas:

- **Modelo de dominio explícito.** La relación 1:N, los estados y el historial se expresan con entidades y enums; los errores de transición se detectan en compilación/ejecución, no en pruebas manuales.
- **Transacciones por caso.** Confirmar una agrupación toca la incidencia, todos sus reportes y el historial: o se aplica todo, o nada. JPA lo resuelve con una sola anotación.
- **Concurrencia.** `@Version` evita que dos operadores se pisen al confirmar el mismo caso; el segundo recibe `409` y recarga. En un prototipo multiusuario esto es más valioso que la velocidad de desarrollo.
- **Actuator + Flyway.** Salud, información de build y migraciones versionadas sin código adicional; el despliegue gana verificabilidad.
- **Separación natural para el motor.** El motor es una clase pura sin JPA ni HTTP, lo que permite las pruebas de C1–C7 sin base de datos.

**Costo asumido:** mayor consumo de memoria que Node (mitigado con `MaxRAMPercentage`) y un *build* más lento (mitigado con caché de capas Docker y `dependency:go-offline`).

---

## 3. Por qué reglas heurísticas y no clustering automático

| Alternativa | Ventaja | Problema para este proyecto |
| --- | --- | --- |
| Clustering espacial puro (DBSCAN, k-means) | Descubre patrones sin reglas | Requiere volumen y coordenadas confiables; no explica por qué agrupó; difícil de auditar |
| Aprendizaje supervisado | Precisión potencial alta | No hay datos etiquetados de Luz del Sur; no hay línea base; caja negra |
| Integración con core/telemedición | Señal real de red | Fuera de alcance (§4.6) y sin accesos |
| **Reglas heurísticas explícitas + revisión humana** | Explicable, configurable, defendible, sin datos históricos | Menor sofisticación; requiere calibrar la ventana |

**Decisión:** reglas declaradas (sector, ventana temporal, tipo) + embeddings como señal auxiliar.

Sustento:

- El informe define exactamente tres variables y un estado de “Baja Certeza” (§4.5); implementar otra cosa rompería la trazabilidad con el diseño validado.
- La tesis del proyecto es *human-in-the-loop*: el sistema **propone**, no decide. Un modelo automático contradiría el principio y el criterio de aceptación H2 (corrección sin pérdida de trazabilidad).
- Cada propuesta guarda motivos y similitud en el reporte, de modo que el operador y el jurado pueden ver **por qué** se agrupó.
- La ventana es configurable (`GROUPING_WINDOW_MINUTES`) para ejecutar H3 con 15, 30 y 60 minutos sin recompilar, tal como pide §7.3.3.

### El rol limitado de los embeddings

La similitud semántica **no** crea vínculos: solo ajusta la certeza cuando las tres reglas ya coinciden. Esto evita dos riesgos:

1. Que una caída del servicio Python impida registrar reportes.
2. Que una similitud alta entre descripciones genéricas (“no hay luz”) una fallas de sectores distintos.

El cliente HTTP tiene timeout de 1,5 s, *cooldown* de 30 s y validación del rango 0–1. Si falla, el motor continúa y degrada la certeza a `MEDIA`; el reporte se guarda igual. La disponibilidad percibida del alta no depende de un modelo pesado.

---

## 4. Por qué un microservicio separado para embeddings

| Opción | Ventaja | Desventaja |
| --- | --- | --- |
| Incrustar el modelo en Java (DJL, ONNX) | Un solo despliegue | Dependencias nativas pesadas, difícil de actualizar, acopla el ciclo de vida |
| Llamar a un servicio externo comercial | Sin infraestructura | Costo, privacidad de descripciones, dependencia de terceros |
| **Microservicio FastAPI + sentence-transformers** | Ecosistema Python, aislamiento de recursos, actualización independiente, fallback sencillo | Un contenedor más y latencia de red |

**Decisión:** microservicio separado (Persona 5). Coincide con la división de trabajo del curso, permite que el backend se pruebe con un mock y aísla el consumo de CPU/RAM del modelo. La comunicación es HTTP/JSON con contrato versionado (`/api/v1/...`), lo que facilita sustituir el modelo sin tocar el dominio.

---

## 5. Por qué PostgreSQL

- **Integridad referencial** entre incidencia, reporte e historial; el prototipo exige no perder reportes.
- **`TIMESTAMPTZ`** para comparar ventanas temporales sin ambigüedad de zona horaria (se opera en UTC y se presenta en `America/Lima`).
- **UUID nativo** para identificadores que se entregan al cliente.
- **Índices parciales/compuestos** para la consulta crítica: `(sector_key, status, last_reported_at)`.
- **Flyway** versiona el esquema; Persona 3 puede agregar usuarios/auditoría con migraciones incrementales.
- Alternativas descartadas: H2/MySQL para desarrollo no ofrecen el mismo soporte de tipos; MongoDB dificultaría las restricciones y la trazabilidad relacional exigida.

---

## 6. Por qué Nginx + Docker Compose + HTTPS

| Necesidad | Decisión |
| --- | --- |
| Un único punto de entrada seguro | Nginx como *reverse proxy* con TLS 1.2/1.3 |
| No exponer base ni microservicio | Red interna de Docker; solo `80/443` publicados |
| Reproducibilidad | `docker-compose.yml` con imágenes fijas y *healthchecks* |
| Secretos fuera del código | `.env` ignorado por Git, permisos `600`, variables inyectadas |
| Abuso del endpoint público | `limit_req` global y más estricto en `POST /api/v1/reports` |
| Renovación de certificados | Certbot + script de renovación con recarga de Nginx |
| Operación sencilla | Scripts de despliegue, respaldo y salud |

Compose se eligió sobre Kubernetes porque el alcance es un servidor propio y un prototipo académico: Kubernetes añadiría complejidad operativa sin aportar valor a la demo. La arquitectura queda preparada para migrar (contenedores sin estado, configuración por entorno, salud verificable).

---

## 7. Decisiones de modelo y API

| Decisión | Justificación |
| --- | --- |
| El reporte se guarda **antes** de agrupar | Garantiza que ningún fallo posterior pierda el dato del ciudadano |
| Vínculo `SUGERIDO` separado de `CONFIRMADO` | Hace visible que la agrupación es una propuesta, no un hecho |
| La incidencia nunca se borra (`DESCARTADA`) | Conserva la historia de decisiones para auditoría |
| `report_link_history` | Responde a H2: cada separación/reasignación es recuperable |
| Estados con transiciones validadas | Evita despachar casos no confirmados o cerrar casos sin atención |
| Respuesta al cliente sin datos internos | Mínimo privilegio cognitivo (§4.2) y menor exposición de datos personales |
| API versionada `/api/v1` | Permite evolucionar sin romper al frontend |
| *Preview* sin persistencia | Facilita pruebas de precisión/exhaustividad (H3) y demos sin contaminar datos |

---

## 8. Cómo esta entrega habilita la validación del informe

| Hipótesis / criterio | Soporte del backend |
| --- | --- |
| H2 — corregir sin perder trazabilidad | Separar/reasignar conserva UUID, reporte e historial; probado en integración |
| H3 — precisión ≥ 80 %, exhaustividad ≥ 70 % | `POST /api/v1/grouping/preview` permite comparar resultado esperado vs. obtenido por caso; ventana configurable |
| H4 — comparar bandeja individual vs. agrupada | `GET /api/v1/operator/reports` (alternativa A) y `GET /api/v1/operator/incidents` (alternativa B) |
| H5 — contexto del técnico | `GET /api/v1/technician/incidents/{id}` con alcance, reportes y advertencias |
| C1–C7 | Cubiertos por pruebas automatizadas y por el flujo de estados |

---

## 9. Riesgos técnicos asumidos

| Riesgo | Impacto | Estado |
| --- | --- | --- |
| Altas concurrentes crean dos casos en lugar de uno | Duplicidad temporal | Aceptado; el operador puede reasignar. Evolución: bloqueo por sector o deduplicación |
| Prioridad calculada en cada evento, no en tiempo real | Orden algo desactualizado | Aceptado en prototipo; evolución: recálculo programado |
| Cabeceras `X-Actor-*` no son autenticación | Suplantación de rol | **Temporal**; Persona 3 implementa JWT y lo reemplaza |
| Modelo de embeddings genérico | Similitudes imprecisas | Señal auxiliar con umbrales; se calibra con datos de Persona 3/5 |
| Sin pruebas de carga | Capacidad desconocida | Fuera de alcance; el diseño permite escalar el backend horizontalmente |
| HTTPS autofirmado en demo | Advertencia del navegador | Solo para demo; producción usa Let's Encrypt |

---

## 10. Conclusión

Spring Boot, PostgreSQL y Nginx/Compose no se eligieron por moda, sino porque cubren los tres requisitos del informe: **probabilidad de probar sin sistemas internos**, **control humano** y **trazabilidad**. El motor heurístico es deliberadamente simple y explicable; los embeddings quedan como una señal auxiliar aislada y tolerante a fallos. La API y el modelo dejan puntos de extensión claros para que Persona 3 (JWT/auditoría), Persona 5 (embeddings reales) y el frontend trabajen en paralelo sin reescribir el núcleo.

El resultado es un backend desplegable en un servidor propio con HTTPS, secretos fuera del código y evidencia automatizada de los casos C1–C7, suficiente para sustentar la demo y las métricas del punto 7 del Avance 2.
