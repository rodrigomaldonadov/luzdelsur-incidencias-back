package pe.edu.upc.luzdelsur.incidencias.report;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import pe.edu.upc.luzdelsur.incidencias.incident.Certainty;
import pe.edu.upc.luzdelsur.incidencias.incident.Incident;

/**
 * Reporte original del cliente. Nunca se elimina: separar o reasignar solo
 * cambia su vinculo y deja rastro en {@link ReportLinkHistory}.
 */
@Entity
@Table(name = "report")
public class Report {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "client_id", length = 64)
    private String clientId;

    @Column(length = 120)
    private String sector;

    @Column(name = "sector_key", length = 120)
    private String sectorKey;

    @Column(length = 200)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_type", nullable = false, length = 40)
    private FailureType failureType;

    @Column(nullable = false, length = 1000)
    private String description;

    @Column(precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "reported_at", nullable = false)
    private Instant reportedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "incident_id")
    private Incident incident;

    @Enumerated(EnumType.STRING)
    @Column(name = "link_status", nullable = false, length = 24)
    private ReportLinkStatus linkStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "link_certainty", length = 16)
    private Certainty linkCertainty;

    @Column(name = "grouping_reason", length = 1000)
    private String groupingReason;

    @Column(name = "similarity_score", precision = 4, scale = 3)
    private BigDecimal similarityScore;

    @Column(name = "link_updated_at")
    private Instant linkUpdatedAt;

    @Column(name = "link_updated_by", length = 64)
    private String linkUpdatedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Report() {
        // JPA
    }

    public static Report register(UUID id, String clientId, String sector, String sectorKey, String reference,
                                  FailureType failureType, String description, BigDecimal latitude,
                                  BigDecimal longitude, Instant reportedAt, Instant now) {
        Report report = new Report();
        report.id = id;
        report.clientId = clientId;
        report.sector = sector;
        report.sectorKey = sectorKey;
        report.reference = reference;
        report.failureType = failureType;
        report.description = description;
        report.latitude = latitude;
        report.longitude = longitude;
        report.reportedAt = reportedAt;
        report.linkStatus = ReportLinkStatus.SUGERIDO;
        report.createdAt = now;
        return report;
    }

    public void linkTo(Incident incident, Certainty certainty, String reason, Double similarity,
                       Instant now, String actorId) {
        this.incident = incident;
        this.linkStatus = ReportLinkStatus.SUGERIDO;
        this.linkCertainty = certainty;
        this.groupingReason = reason;
        this.similarityScore = similarity == null ? null : BigDecimal.valueOf(similarity);
        this.linkUpdatedAt = now;
        this.linkUpdatedBy = actorId;
    }

    public void confirmLink(Instant now, String actorId) {
        this.linkStatus = ReportLinkStatus.CONFIRMADO;
        this.linkUpdatedAt = now;
        this.linkUpdatedBy = actorId;
    }

    public void detach(Instant now, String actorId) {
        this.incident = null;
        this.linkStatus = ReportLinkStatus.SEPARADO;
        this.linkUpdatedAt = now;
        this.linkUpdatedBy = actorId;
    }

    public void moveTo(Incident target, Certainty certainty, String reason, Instant now, String actorId) {
        this.incident = target;
        this.linkStatus = ReportLinkStatus.REASIGNADO;
        this.linkCertainty = certainty;
        this.groupingReason = reason;
        this.linkUpdatedAt = now;
        this.linkUpdatedBy = actorId;
    }

    public UUID getId() {
        return id;
    }

    public String getClientId() {
        return clientId;
    }

    public String getSector() {
        return sector;
    }

    public String getSectorKey() {
        return sectorKey;
    }

    public String getReference() {
        return reference;
    }

    public FailureType getFailureType() {
        return failureType;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public Instant getReportedAt() {
        return reportedAt;
    }

    public Incident getIncident() {
        return incident;
    }

    public ReportLinkStatus getLinkStatus() {
        return linkStatus;
    }

    public Certainty getLinkCertainty() {
        return linkCertainty;
    }

    public String getGroupingReason() {
        return groupingReason;
    }

    public Double getSimilarityScore() {
        return similarityScore == null ? null : similarityScore.doubleValue();
    }

    public Instant getLinkUpdatedAt() {
        return linkUpdatedAt;
    }

    public String getLinkUpdatedBy() {
        return linkUpdatedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
