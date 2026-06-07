package biali.fitmanager.backend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
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
    private final AppUserRepository appUserRepository;

    public JwtService(AppUserRepository appUserRepository,
                      @Value("${app.jwt.secret}") String jwtSecret,
                      @Value("${app.jwt.expiration-ms:86400000}") long expirationTime) {
        this.appUserRepository = appUserRepository;
        this.signingKey = createSigningKey(jwtSecret);
        this.expirationTime = expirationTime;
    }

    /**
     * Generuje token JWT dla zalogowanego użytkownika.
     *
     * @param authentication dane uwierzytelnienia Spring Security
     * @return podpisany token JWT z emailem, rolami i imieniem
     */
    public String generateToken(Authentication authentication) {
        String email = authentication.getName();
        
        String roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(","));

        AppUser user = appUserRepository.findByEmail(email).orElse(null);
        String firstName = user != null ? user.getFirstName() : null;
        String lastName = user != null ? user.getLastName() : null;
        String fullName = user != null ? (safe(firstName) + " " + safe(lastName)).trim() : null;

        return Jwts.builder()
                .setSubject(email)
                .claim("roles", roles)
            .claim("firstName", firstName)
            .claim("lastName", lastName)
            .claim("name", fullName)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expirationTime))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Zwraca pusty ciąg zamiast null.
     *
     * @param value tekst do zabezpieczenia
     * @return wartość lub pusty ciąg
     */
        private String safe(String value) {
        return value == null ? "" : value;
        }

    /**
     * Wyciąga email (subject) z tokenu JWT.
     *
     * @param token token JWT
     * @return adres email użytkownika
     */
    public String extractEmail(String token) {
        return extractAllClaims(token).getSubject();
    }

    /**
     * Sprawdza, czy token JWT jest ważny dla danego użytkownika.
     *
     * @param token token JWT
     * @param userDetails dane użytkownika do porównania
     * @return true gdy token poprawny i nie wygasł
     */
    public boolean isTokenValid(String token, UserDetails userDetails) {
        try {
            String email = extractEmail(token);
            return email.equals(userDetails.getUsername()) && !isTokenExpired(token);
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
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
}