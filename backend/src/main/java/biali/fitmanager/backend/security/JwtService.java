package biali.fitmanager.backend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import biali.fitmanager.backend.model.AppUser;
import biali.fitmanager.backend.repository.AppUserRepository;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.stream.Collectors;

/**
 * Generowanie i walidacja tokenów JWT.
 */
@Service
public class JwtService {

    private final Key signingKey;
    private final long expirationTime;

    @Autowired
    public JwtService(@Value("${app.jwt.secret}") String jwtSecret,
                      @Value("${app.jwt.expiration-ms:86400000}") long expirationTime) {
        this.signingKey = createSigningKey(jwtSecret);
        this.expirationTime = expirationTime;
    }

    /**
     * Konstruktor kompatybilnościowy dla starszych testów jednostkowych.
     * Repozytorium nie jest już potrzebne do budowy tokena.
     */
    public JwtService(AppUserRepository ignoredRepository, String jwtSecret, long expirationTime) {
        this(jwtSecret, expirationTime);
    }

    /**
     * Generuje minimalny token JWT używany po poprawnym logowaniu.
     *
     * Payload zawiera wyłącznie dwa biznesowe claime:
     * - {@code id}: identyfikator użytkownika z bazy
     * - {@code role}: rola użytkownika (np. CLIENT, TRAINER, ADMIN)
     *
     * Oprócz tego token ma standardowe pola techniczne:
     * - {@code iat} (issued at)
     * - {@code exp} (expiration)
     */
    public String generateToken(AppUser user) {
        String role = normalizeRole(user.getRole());

        return Jwts.builder()
                .claim("id", user.getId())
                .claim("role", role)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expirationTime))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Metoda kompatybilnościowa dla starszego kodu testowego.
     * Jeżeli principal jest typu {@link AppUser}, token nadal zawiera id/role z encji.
     * W przeciwnym razie tworzy token z technicznym id=0 i rolą wynikającą z authorities.
     */
    public String generateToken(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof AppUser) {
            return generateToken((AppUser) principal);
        }

        String roleFromAuthorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(this::normalizeRole)
                .filter(r -> !r.isBlank())
                .collect(Collectors.joining(","));
        String role = roleFromAuthorities.contains(",")
                ? roleFromAuthorities.substring(0, roleFromAuthorities.indexOf(','))
                : roleFromAuthorities;

        return Jwts.builder()
                .claim("id", 0)
                .claim("role", role)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expirationTime))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Odczytuje id użytkownika z tokenu JWT.
     */
    public Integer extractUserId(String token) {
        Claims claims = extractAllClaims(token);
        Object idClaim = claims.get("id");
        if (idClaim == null) {
            return null;
        }
        if (idClaim instanceof Integer) {
            return (Integer) idClaim;
        }
        if (idClaim instanceof Number) {
            return ((Number) idClaim).intValue();
        }
        try {
            return Integer.parseInt(idClaim.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * Odczytuje rolę użytkownika z tokenu JWT.
     */
    public String extractRole(String token) {
        Object roleClaim = extractAllClaims(token).get("role");
        return roleClaim == null ? null : normalizeRole(roleClaim.toString());
    }

    /**
     * Metoda kompatybilnościowa (stary kontrakt oparty o email w subject).
     * W nowym formacie tokenu subject nie jest ustawiany, więc zwraca null.
     */
    public String extractEmail(String token) {
        return extractAllClaims(token).getSubject();
    }

    /**
     * Sprawdza integralność podpisu i datę wygaśnięcia tokenu.
     */
    public boolean isTokenValid(String token) {
        try {
            return !isTokenExpired(token);
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    /**
     * Metoda kompatybilnościowa ze starszym podpisem.
     * Obecnie walidacja nie porównuje username, bo token nie zawiera emaila.
     */
    public boolean isTokenValid(String token, UserDetails ignoredUserDetails) {
        return isTokenValid(token);
    }

    private boolean isTokenExpired(String token) {
        Date expiration = extractAllClaims(token).getExpiration();
        return expiration.before(new Date());
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(signingKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    private Key createSigningKey(String secret) {
        try {
            byte[] keyBytes = Decoders.BASE64.decode(secret);
            return Keys.hmacShaKeyFor(keyBytes);
        } catch (RuntimeException ex) {
            byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
            return Keys.hmacShaKeyFor(keyBytes);
        }
    }

    private String normalizeRole(String role) {
        if (role == null) {
            return "";
        }
        return role.replace("ROLE_", "").toUpperCase();
    }
}