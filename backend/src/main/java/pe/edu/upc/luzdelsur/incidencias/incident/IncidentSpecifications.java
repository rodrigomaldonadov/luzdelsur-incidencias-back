package pe.edu.upc.luzdelsur.incidencias.incident;

import java.time.Instant;
import java.util.Locale;

import org.springframework.data.jpa.domain.Specification;

import pe.edu.upc.luzdelsur.incidencias.common.TextNormalizer;
import pe.edu.upc.luzdelsur.incidencias.report.FailureType;

public final class IncidentSpecifications {

    private IncidentSpecifications() {
    }

    public static Specification<Incident> hasStatus(IncidentStatus status) {
        return status == null ? null : (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Incident> isOpen() {
        return (root, query, cb) -> root.get("status").in(
                IncidentStatus.SUGERIDA, IncidentStatus.CONFIRMADA,
                IncidentStatus.EN_ATENCION, IncidentStatus.REABIERTA);
    }

    public static Specification<Incident> hasCertainty(Certainty certainty) {
        return certainty == null ? null : (root, query, cb) -> cb.equal(root.get("certainty"), certainty);
    }

    public static Specification<Incident> requiresReview(Boolean value) {
        return value == null ? null : (root, query, cb) -> cb.equal(root.get("requiresReview"), value);
    }

    public static Specification<Incident> hasFailureType(FailureType failureType) {
        return failureType == null ? null : (root, query, cb) -> cb.equal(root.get("failureType"), failureType);
    }

    public static Specification<Incident> sectorContains(String sector) {
        if (TextNormalizer.normalizeKey(sector) == null) {
            return null;
        }
        String pattern = "%" + TextNormalizer.normalizeKey(sector).toLowerCase(Locale.ROOT) + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("sectorKey")), pattern);
    }
}
