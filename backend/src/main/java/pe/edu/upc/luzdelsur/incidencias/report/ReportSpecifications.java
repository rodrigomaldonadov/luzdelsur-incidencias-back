package pe.edu.upc.luzdelsur.incidencias.report;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import pe.edu.upc.luzdelsur.incidencias.common.TextNormalizer;

public final class ReportSpecifications {

    private ReportSpecifications() {
    }

    public static Specification<Report> hasIncident(UUID incidentId) {
        return incidentId == null ? null : (root, query, cb) -> cb.equal(root.get("incident").get("id"), incidentId);
    }

    public static Specification<Report> hasLinkStatus(ReportLinkStatus status) {
        return status == null ? null : (root, query, cb) -> cb.equal(root.get("linkStatus"), status);
    }

    public static Specification<Report> hasFailureType(FailureType failureType) {
        return failureType == null ? null : (root, query, cb) -> cb.equal(root.get("failureType"), failureType);
    }

    public static Specification<Report> sectorContains(String sector) {
        if (TextNormalizer.normalizeKey(sector) == null) {
            return null;
        }
        String pattern = "%" + TextNormalizer.normalizeKey(sector).toLowerCase(Locale.ROOT) + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("sectorKey")), pattern);
    }

    public static Specification<Report> reportedFrom(Instant from) {
        return from == null ? null : (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("reportedAt"), from);
    }

    public static Specification<Report> reportedTo(Instant to) {
        return to == null ? null : (root, query, cb) -> cb.lessThanOrEqualTo(root.get("reportedAt"), to);
    }
}
