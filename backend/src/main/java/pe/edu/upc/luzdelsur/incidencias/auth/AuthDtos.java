package pe.edu.upc.luzdelsur.incidencias.auth;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import pe.edu.upc.luzdelsur.incidencias.security.ActorRole;

public final class AuthDtos {

    private AuthDtos() {
    }

    /**
     * {@code role} es opcional: si el formulario lo envia y no coincide con el
     * rol de la cuenta, el login se rechaza con un mensaje claro.
     */
    public record LoginRequest(
            @NotBlank @Size(max = 120) String username,
            @NotBlank @Size(max = 72) String password,
            ActorRole role) {
    }

    /**
     * Registro publico. Sin {@code role} se crea un CLIENTE. OPERADOR y TECNICO
     * solo se aceptan si {@code app.auth.allow-staff-registration=true}.
     */
    public record RegisterRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Size(max = 120) String username,
            @NotBlank @Size(min = 6, max = 72) String password,
            ActorRole role) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record AuthUser(
            String id,
            String name,
            String username,
            ActorRole role) {

        static AuthUser from(AppUser user) {
            return new AuthUser(user.getId().toString(), user.getFullName(), user.getUsername(), user.getRole());
        }
    }

    /**
     * {@code token} y {@code user} son los campos que ya consume el frontend;
     * el resto permite renovar la sesion sin volver a pedir la contrasena.
     */
    public record AuthResponse(
            String token,
            String tokenType,
            long expiresIn,
            Instant expiresAt,
            String refreshToken,
            Instant refreshExpiresAt,
            AuthUser user) {
    }

    /** Alta de personal por un operador (gestion de tecnicos). */
    public record StaffCreateRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Size(max = 120) String username,
            @NotBlank @Size(min = 6, max = 72) String password) {
    }

    public record StaffUpdateRequest(Boolean active) {
    }

    public record TechnicianView(
            UUID id,
            String name,
            String username,
            boolean active,
            long openAssignments,
            Instant lastLoginAt) {
    }
}
