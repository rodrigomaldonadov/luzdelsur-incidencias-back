-- =====================================================================
-- V3 - Usuarios y roles (autenticacion JWT)
--
-- El identificador del usuario (UUID) es el "sub" del token y el valor que
-- el backend guarda como dueno del reporte (report.client_id), como actor del
-- historial y como tecnico asignado. Por eso cabe en VARCHAR(64).
-- La contrasena se guarda solo como hash BCrypt.
-- =====================================================================

CREATE TABLE app_user (
    id                  UUID PRIMARY KEY,
    username            VARCHAR(120) NOT NULL,
    full_name           VARCHAR(120) NOT NULL,
    password_hash       VARCHAR(100) NOT NULL,
    role                VARCHAR(16)  NOT NULL,
    active              BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    last_login_at       TIMESTAMP WITH TIME ZONE
);

CREATE UNIQUE INDEX ux_app_user_username ON app_user (username);
CREATE INDEX idx_app_user_role ON app_user (role, active);

COMMENT ON TABLE app_user IS 'Cuentas de cliente, operador y tecnico; el UUID es el sub del JWT';
