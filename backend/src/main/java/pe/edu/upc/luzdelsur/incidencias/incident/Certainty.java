package pe.edu.upc.luzdelsur.incidencias.incident;

import java.util.Collection;

public enum Certainty {

    ALTA,
    MEDIA,
    BAJA;

    /**
     * La certeza de la incidencia es la peor de sus reportes:
     * una sola ubicacion incompleta obliga a revisar el caso completo.
     */
    public static Certainty worstOf(Collection<Certainty> values) {
        if (values == null || values.isEmpty()) {
            return ALTA;
        }
        if (values.contains(BAJA)) {
            return BAJA;
        }
        if (values.contains(MEDIA)) {
            return MEDIA;
        }
        return ALTA;
    }
}
