package pe.edu.upc.luzdelsur.incidencias.incident;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import pe.edu.upc.luzdelsur.incidencias.report.FailureType;

/**
 * Incidencia padre. Se conserva incluso si el operador separa todos sus
 * reportes (pasa a DESCARTADA) para no perder trazabilidad.
 */
@Entity
@Table(name = "incident")
public class Incident {

    public static final String UNKNOWN_SECTOR_KEY = "SIN SECTOR";
    public static final String UNKNOWN_SECTOR_LABEL = "Sin sector informado";

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, length = 120)
    private String sector;

    @Column(name = "sector_key", nullable = false, length = 120)
    private String sectorKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_type", nullable = false, length = 40)
    private FailureType failureType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private IncidentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Certainty certainty;

    @Column(name = "requires_review", nullable = false)
    private boolean requiresReview;

    @Column(name = "priority_score", nullable = false)
    private double priorityScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority_label", nullable = false, length = 16)
    private PriorityLabel priorityLabel;

    @Column(name = "report_count", nullable = false)
    private int reportCount;

    @Column(name = "first_reported_at", nullable = false)
    private Instant firstReportedAt;

    @Column(name = "last_reported_at", nullable = false)
    private Instant lastReportedAt;

    @Column(name = "status_updated_at")
    private Instant statusUpdatedAt;

    @Column(name = "status_updated_by", length = 64)
    private String statusUpdatedBy;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "confirmed_by", length = 64)
    private String confirmedBy;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "assigned_technician_id", length = 64)
    private String assignedTechnicianId;

    @Column(name = "assigned_at")
    private Instant assignedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected Incident() {
        // JPA
    }

    public static Incident start(UUID id, String sector, String sectorKey, FailureType failureType,
                                 Instant reportedAt, Certainty certainty, Instant now) {
        Incident incident = new Incident();
        incident.id = id;
        incident.sector = sector;
        incident.sectorKey = sectorKey;
        incident.failureType = failureType;
        incident.status = IncidentStatus.SUGERIDA;
        incident.certainty = certainty;
        incident.requiresReview = certainty == Certainty.BAJA;
        incident.priorityScore = 0;
        incident.priorityLabel = PriorityLabel.BAJA;
        incident.reportCount = 1;
        incident.firstReportedAt = reportedAt;
        incident.lastReportedAt = reportedAt;
        incident.statusUpdatedAt = now;
        incident.statusUpdatedBy = "sistema";
        incident.createdAt = now;
        incident.updatedAt = now;
        return incident;
    }

    /**
     * Recalcula contadores, certeza y prioridad a partir de los reportes vigentes.
     */
    public void updateMetrics(int reportCount, Instant firstReportedAt, Instant lastReportedAt,
                              Certainty certainty, PriorityScore priority, Instant now) {
        this.reportCount = reportCount;
        if (reportCount > 0 && firstReportedAt != null && lastReportedAt != null) {
            this.firstReportedAt = firstReportedAt;
            this.lastReportedAt = lastReportedAt;
        }
        this.certainty = certainty;
        this.priorityScore = priority.score();
        this.priorityLabel = priority.label();
        this.updatedAt = now;
        refreshReviewFlag();
    }

    public void markConfirmed(String actorId, Instant now) {
        this.status = IncidentStatus.CONFIRMADA;
        this.confirmedAt = now;
        this.confirmedBy = actorId;
        this.statusUpdatedAt = now;
        this.statusUpdatedBy = actorId;
        this.updatedAt = now;
        refreshReviewFlag();
    }

    public void changeStatus(IncidentStatus target, String actorId, Instant now) {
        this.status = target;
        this.statusUpdatedAt = now;
        this.statusUpdatedBy = actorId;
        this.updatedAt = now;
        if (target == IncidentStatus.CERRADA) {
            this.closedAt = now;
        }
        if (target == IncidentStatus.REABIERTA) {
            this.closedAt = null;
        }
        refreshReviewFlag();
    }

    public void applySectorIfMissing(String sector, String sectorKey) {
        if (UNKNOWN_SECTOR_KEY.equals(this.sectorKey) && !UNKNOWN_SECTOR_KEY.equals(sectorKey)) {
            this.sector = sector;
            this.sectorKey = sectorKey;
        }
    }

    /**
     * El tecnico que inicia la atencion queda asociado al caso. No es una
     * asignacion automatica de flota: es la constancia de quien tomo el trabajo.
     */
    public void assignTechnician(String technicianId, Instant now) {
        this.assignedTechnicianId = technicianId;
        this.assignedAt = now;
        this.updatedAt = now;
    }

    private void refreshReviewFlag() {
        this.requiresReview = certainty == Certainty.BAJA && status.isOpen();
    }

    public UUID getId() {
        return id;
    }

    public String getSector() {
        return sector;
    }

    public String getSectorKey() {
        return sectorKey;
    }

    public FailureType getFailureType() {
        return failureType;
    }

    public IncidentStatus getStatus() {
        return status;
    }

    public Certainty getCertainty() {
        return certainty;
    }

    public boolean isRequiresReview() {
        return requiresReview;
    }

    public double getPriorityScore() {
        return priorityScore;
    }

    public PriorityLabel getPriorityLabel() {
        return priorityLabel;
    }

    public int getReportCount() {
        return reportCount;
    }

    public Instant getFirstReportedAt() {
        return firstReportedAt;
    }

    public Instant getLastReportedAt() {
        return lastReportedAt;
    }

    public Instant getStatusUpdatedAt() {
        return statusUpdatedAt;
    }

    public String getStatusUpdatedBy() {
        return statusUpdatedBy;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public String getConfirmedBy() {
        return confirmedBy;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public String getAssignedTechnicianId() {
        return assignedTechnicianId;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }
}
