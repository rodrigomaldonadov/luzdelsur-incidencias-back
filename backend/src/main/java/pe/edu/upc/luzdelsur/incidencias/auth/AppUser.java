package pe.edu.upc.luzdelsur.incidencias.auth;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import pe.edu.upc.luzdelsur.incidencias.security.ActorRole;

/**
 * Cuenta de acceso. El {@code id} es el "sub" del JWT y el identificador que
 * el resto del dominio usa como actor (dueno del reporte, tecnico asignado).
 */
@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, length = 120)
    private String username;

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ActorRole role;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    protected AppUser() {
        // JPA
    }

    public static AppUser create(String username, String fullName, String passwordHash,
                                 ActorRole role, Instant now) {
        AppUser user = new AppUser();
        user.id = UUID.randomUUID();
        user.username = username;
        user.fullName = fullName;
        user.passwordHash = passwordHash;
        user.role = role;
        user.active = true;
        user.createdAt = now;
        return user;
    }

    public void markLogin(Instant now) {
        this.lastLoginAt = now;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public UUID getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getFullName() {
        return fullName;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public ActorRole getRole() {
        return role;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }
}
