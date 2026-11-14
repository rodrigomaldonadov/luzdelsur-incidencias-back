package pe.edu.upc.luzdelsur.incidencias.common;

import java.time.Instant;
import java.util.List;

public record ApiError(
        Instant timestamp,
        int status,
        String code,
        String message,
        List<String> details,
        String path) {
}
