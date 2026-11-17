package pe.edu.upc.luzdelsur.incidencias.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import pe.edu.upc.luzdelsur.incidencias.security.AccessControl;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Autenticacion", description = "Registro, inicio de sesion y renovacion de JWT")
public class AuthController {

    private final AuthService authService;
    private final AccessControl accessControl;

    public AuthController(AuthService authService, AccessControl accessControl) {
        this.authService = authService;
        this.accessControl = accessControl;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crea una cuenta (por defecto CLIENTE) y devuelve la sesion")
    public AuthDtos.AuthResponse register(@Valid @RequestBody AuthDtos.RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    @Operation(summary = "Inicia sesion con usuario y contrasena; valida el rol si se envia")
    public AuthDtos.AuthResponse login(@Valid @RequestBody AuthDtos.LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Renueva el token de acceso con el token de refresco")
    public AuthDtos.AuthResponse refresh(@Valid @RequestBody AuthDtos.RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @GetMapping("/me")
    @Operation(summary = "Usuario de la sesion actual (requiere Authorization: Bearer)")
    public AuthDtos.AuthUser me() {
        return authService.me(accessControl.currentActor());
    }
}
