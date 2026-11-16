package pe.edu.upc.luzdelsur.incidencias.common;

/**
 * Credenciales ausentes, invalidas o vencidas (HTTP 401). Se distingue de
 * {@link ForbiddenException}, que indica identidad valida sin permiso (403).
 */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
