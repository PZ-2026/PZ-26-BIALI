package biali.fitmanager.backend.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import biali.fitmanager.backend.model.AppUser;
import biali.fitmanager.backend.repository.AppUserRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("Testy JwtService")
class JwtServiceTest {

    private static final String SECRET = "fitmanager-super-secret-key-change-me-2026-very-long";

    @Mock
    private AppUserRepository appUserRepository;

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(appUserRepository, SECRET, 60_000L);
    }

    @Test
    @DisplayName("Generowanie tokenu i walidacja dla tego samego uzytkownika")
    void generateTokenAndValidateTokenForSameUser() {
        AppUser appUser = new AppUser();
        appUser.setEmail("user@example.com");
        appUser.setFirstName("Anna");
        appUser.setLastName("Nowak");

        when(appUserRepository.findByEmail("user@example.com")).thenReturn(Optional.of(appUser));

        Authentication authentication = new UsernamePasswordAuthenticationToken(
                "user@example.com",
                "secret",
                List.of(new SimpleGrantedAuthority("ROLE_CLIENT"))
        );

        String token = jwtService.generateToken(authentication);
        UserDetails userDetails = User.withUsername("user@example.com")
                .password("irrelevant")
                .authorities("ROLE_CLIENT")
                .build();

        assertNotNull(token);
        assertEquals("user@example.com", jwtService.extractEmail(token));
        assertTrue(jwtService.isTokenValid(token, userDetails));
    }

    @Test
    @DisplayName("Token jest niewazny dla innego uzytkownika")
    void tokenIsInvalidForDifferentUser() {
        when(appUserRepository.findByEmail("user@example.com")).thenReturn(Optional.empty());

        Authentication authentication = new UsernamePasswordAuthenticationToken(
                "user@example.com",
                "secret",
                List.of(new SimpleGrantedAuthority("ROLE_CLIENT"))
        );

        String token = jwtService.generateToken(authentication);
        UserDetails otherUser = User.withUsername("other@example.com")
                .password("irrelevant")
                .authorities("ROLE_CLIENT")
                .build();

        assertFalse(jwtService.isTokenValid(token, otherUser));
    }

    @Test
    @DisplayName("Niepoprawny token zwraca false")
    void invalidTokenReturnsFalse() {
        UserDetails userDetails = User.withUsername("user@example.com")
                .password("irrelevant")
                .authorities("ROLE_CLIENT")
                .build();

        assertFalse(jwtService.isTokenValid("this-is-not-a-jwt", userDetails));
    }
}
