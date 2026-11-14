# Mock de embeddings (solo desarrollo)

Servicio temporal, sin dependencias externas, que implementa el contrato HTTP
acordado con Persona 5. Se usa con `docker-compose.dev.yml` para demostrar la
integracion sin descargar el modelo de sentence-transformers.

**No es el entregable de Persona 5.** La similitud es lexica (Jaccard), por lo
que no captura parafrasis; su unico objetivo es validar el flujo y los tiempos
de espera del backend.

Endpoints:

- `GET /health`
- `POST /api/v1/similarity` con `{"text_a": "...", "text_b": "..."}`
- `POST /api/v1/similarity/batch` con `{"text": "...", "candidates": ["..."]}`
