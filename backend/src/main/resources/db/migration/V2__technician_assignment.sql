-- =====================================================================
-- V2 - Asignacion del tecnico de campo
--
-- Persona 1: modelo minimo para que la bandeja del tecnico no sea solo
-- una lista por estado. Al iniciar la atencion (EN_ATENCION) se registra
-- el tecnico que tomo el caso.
-- Persona 3 puede extender con usuarios/roles en V3+.
-- =====================================================================

ALTER TABLE incident
    ADD COLUMN assigned_technician_id VARCHAR(64);

ALTER TABLE incident
    ADD COLUMN assigned_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX idx_incident_technician
    ON incident (assigned_technician_id, status, priority_score DESC);

COMMENT ON COLUMN incident.assigned_technician_id IS 'Tecnico que inicio la atencion del caso';
COMMENT ON COLUMN incident.assigned_at IS 'Momento en que el tecnico tomo el caso';
