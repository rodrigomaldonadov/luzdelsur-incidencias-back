package pe.edu.upc.luzdelsur.incidencias.report;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "report_link_history")
public class ReportLinkHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "report_id", nullable = false)
    private UUID reportId;

    @Column(name = "from_incident_id")
    private UUID fromIncidentId;

    @Column(name = "to_incident_id")
    private UUID toIncidentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private LinkAction action;

    @Column(name = "actor_id", length = 64)
    private String actorId;

    @Column(length = 500)
    private String note;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ReportLinkHistory() {
        // JPA
    }

    public static ReportLinkHistory record(UUID reportId, UUID fromIncidentId, UUID toIncidentId,
                                           LinkAction action, String actorId, String note, Instant now) {
        ReportLinkHistory history = new ReportLinkHistory();
        history.reportId = reportId;
        history.fromIncidentId = fromIncidentId;
        history.toIncidentId = toIncidentId;
        history.action = action;
        history.actorId = actorId;
        history.note = note;
        history.createdAt = now;
        return history;
    }

    public Long getId() {
        return id;
    }

    public UUID getReportId() {
        return reportId;
    }

    public UUID getFromIncidentId() {
        return fromIncidentId;
    }

    public UUID getToIncidentId() {
        return toIncidentId;
    }

    public LinkAction getAction() {
        return action;
    }

    public String getActorId() {
        return actorId;
    }

    public String getNote() {
        return note;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
