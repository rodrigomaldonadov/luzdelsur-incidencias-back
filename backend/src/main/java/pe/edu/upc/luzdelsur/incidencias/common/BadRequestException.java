package pe.edu.upc.luzdelsur.incidencias.common;

public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
