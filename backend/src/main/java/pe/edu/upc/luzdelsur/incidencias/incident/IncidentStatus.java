package pe.edu.upc.luzdelsur.incidencias.incident;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Estados del caso. La maquina de estados evita saltos invalidos
 * (por ejemplo, despachar un caso que el operador aun no confirmo).
 */
public enum IncidentStatus {

    SUGERIDA,
    CONFIRMADA,
    EN_ATENCION,
    RESUELTA,
    CERRADA,
    REABIERTA,
    DESCARTADA;

    private static final Map<IncidentStatus, Set<IncidentStatus>> ALLOWED = new EnumMap<>(IncidentStatus.class);

    static {
        ALLOWED.put(SUGERIDA, EnumSet.of(CONFIRMADA, DESCARTADA));
        ALLOWED.put(CONFIRMADA, EnumSet.of(EN_ATENCION, DESCARTADA));
        ALLOWED.put(EN_ATENCION, EnumSet.of(RESUELTA));
        ALLOWED.put(RESUELTA, EnumSet.of(CERRADA, REABIERTA));
        ALLOWED.put(CERRADA, EnumSet.of(REABIERTA));
        ALLOWED.put(REABIERTA, EnumSet.of(EN_ATENCION, CERRADA));
        ALLOWED.put(DESCARTADA, EnumSet.noneOf(IncidentStatus.class));
    }

    public boolean canTransitionTo(IncidentStatus target) {
        return ALLOWED.getOrDefault(this, Set.of()).contains(target);
    }

    public boolean isOpen() {
        return this == SUGERIDA || this == CONFIRMADA || this == EN_ATENCION || this == REABIERTA;
    }

    public boolean canReceiveReports() {
        return isOpen();
    }
}
