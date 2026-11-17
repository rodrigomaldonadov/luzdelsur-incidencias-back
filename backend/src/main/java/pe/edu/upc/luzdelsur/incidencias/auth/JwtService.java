package pe.edu.upc.luzdelsur.incidencias.auth;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Locale;

import javax.crypto.SecretKey;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import pe.edu.upc.luzdelsur.incidencias.common.UnauthorizedException;
import pe.edu.upc.luzdelsur.incidencias.config.AppProperties;
import pe.edu.upc.luzdelsur.incidencias.security.Actor;
import pe.edu.upc.luzdelsur.incidencias.security.ActorRole;

/**
 * Emite y valida tokens HS256.
 *
 * <ul>
 *   <li>Acceso: {@code sub} = id del usuario, {@code role}, {@code name}, {@code typ=access}.</li>
 *   <li>Refresco: {@code sub} y {@code typ=refresh}; el rol se relee de la base al refrescar,
 *       asi un cambio de rol o una cuenta desactivada no sobreviven al refresco.</li>
 * </ul>
 */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    static final String CLAIM_ROLE = "role";
    static final String CLAIM_NAME = "name";
    static final String CLAIM_TYPE = "typ";
    static final String TYPE_ACCESS = "access";
    static final String TYPE_REFRESH = "refresh";

    private final SecretKey key;
    private final String issuer;
    private final Duration accessTtl;
    private final Duration refreshTtl;
    private final Clock clock;

    public JwtService(AppProperties properties, Clock clock) {
        AppProperties.Jwt jwt = properties.getSecurity().getJwt();
        this.key = buildKey(jwt.getSecret());
        this.issuer = jwt.getIssuer();
        this.accessTtl = Duration.ofMinutes(Math.max(jwt.getAccessTtlMinutes(), 1));
        this.refreshTtl = Duration.ofDays(Math.max(jwt.getRefreshTtlDays(), 1));
        this.clock = clock;
    }

    public IssuedToken issueAccess(AppUser user) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(accessTtl);
        String token = Jwts.builder()
                .issuer(issuer)
                .subject(user.getId().toString())
                .claim(CLAIM_ROLE, user.getRole().name())
                .claim(CLAIM_NAME, user.getFullName())
                .claim(CLAIM_TYPE, TYPE_ACCESS)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact();
        return new IssuedToken(token, expiresAt);
    }

    public IssuedToken issueRefresh(AppUser user) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(refreshTtl);
        String token = Jwts.builder()
                .issuer(issuer)
                .subject(user.getId().toString())
                .claim(CLAIM_TYPE, TYPE_REFRESH)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact();
        return new IssuedToken(token, expiresAt);
    }

    /** Valida un token de acceso y devuelve el actor (sub + rol). */
    public Actor parseAccess(String token) {
        Claims claims = parse(token, TYPE_ACCESS);
        String roleValue = claims.get(CLAIM_ROLE, String.class);
        if (roleValue == null) {
            throw new UnauthorizedException("Token sin rol");
        }
        try {
            return new Actor(claims.getSubject(), ActorRole.valueOf(roleValue.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ex) {
            throw new UnauthorizedException("Token con rol no valido");
        }
    }

    /** Valida un token de refresco y devuelve el id del usuario. */
    public String parseRefreshSubject(String token) {
        return parse(token, TYPE_REFRESH).getSubject();
    }

    public long accessTtlSeconds() {
        return accessTtl.toSeconds();
    }

    private Claims parse(String token, String expectedType) {
        Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(issuer)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException ex) {
            throw new UnauthorizedException("La sesion expiro. Vuelva a iniciar sesion");
        } catch (JwtException | IllegalArgumentException ex) {
            throw new UnauthorizedException("Token no valido");
        }
        if (!expectedType.equals(claims.get(CLAIM_TYPE, String.class))) {
            throw new UnauthorizedException("Tipo de token no valido para esta operacion");
        }
        if (claims.getSubject() == null || claims.getSubject().isBlank()) {
            throw new UnauthorizedException("Token sin sujeto");
        }
        return claims;
    }

    private static SecretKey buildKey(String secret) {
        byte[] bytes;
        if (secret == null || secret.isBlank()) {
            bytes = new byte[48];
            new SecureRandom().nextBytes(bytes);
            log.warn("APP_JWT_SECRET no esta configurado: se genero un secreto aleatorio. "
                    + "Los tokens dejaran de valer al reiniciar el backend.");
        } else {
            bytes = secret.getBytes(StandardCharsets.UTF_8);
            if (bytes.length < 32) {
                throw new IllegalStateException(
                        "APP_JWT_SECRET debe tener al menos 32 caracteres (HS256). Longitud actual: "
                                + bytes.length + ". Ejemplo: openssl rand -base64 48");
            }
        }
        return Keys.hmacShaKeyFor(bytes);
    }

    public record IssuedToken(String token, Instant expiresAt) {
    }
}
