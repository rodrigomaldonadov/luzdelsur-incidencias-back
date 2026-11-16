package pe.edu.upc.luzdelsur.incidencias.report;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Calificacion del cliente sobre la atencion de su reporte (una por reporte). */
@Entity
@Table(name = "report_feedback")
public class ReportFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "report_id", nullable = false)
    private UUID reportId;

    @Column(nullable = false)
    private short rating;

    @Column(length = 500)
    private String comment;

    @Column(name = "actor_id", length = 64)
    private String actorId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ReportFeedback() {
        // JPA
    }

    public static ReportFeedback of(UUID reportId, int rating, String comment, String actorId, Instant now) {
        ReportFeedback feedback = new ReportFeedback();
        feedback.reportId = reportId;
        feedback.rating = (short) rating;
        feedback.comment = comment;
        feedback.actorId = actorId;
        feedback.createdAt = now;
        return feedback;
    }

    public Long getId() {
        return id;
    }

    public UUID getReportId() {
        return reportId;
    }

    public int getRating() {
        return rating;
    }

    public String getComment() {
        return comment;
    }

    public String getActorId() {
        return actorId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
