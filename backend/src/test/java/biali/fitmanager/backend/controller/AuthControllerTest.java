package biali.fitmanager.backend.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import biali.fitmanager.backend.dto.AuthErrorResponse;
import biali.fitmanager.backend.dto.LoginRequest;
import biali.fitmanager.backend.dto.LoginResponse;
import biali.fitmanager.backend.dto.RegisterRequest;
import biali.fitmanager.backend.model.AppUser;
import biali.fitmanager.backend.repository.AppUserRepository;
import biali.fitmanager.backend.security.JwtService;

@ExtendWith(MockitoExtension.class)
@DisplayName("Testy kontrolera uwierzytelniania")
class AuthControllerTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtService jwtService;

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private AuthController authController;

    @BeforeEach
    void setUp() {
        authController = new AuthController(authenticationManager, jwtService, appUserRepository, passwordEncoder);
    }

    @Test
    @DisplayName("Logowanie zwraca token przy poprawnych danych")
    void loginReturnsTokenWhenCredentialsAreValid() {
        LoginRequest request = new LoginRequest();
        request.setEmail("user@example.com");
        request.setPassword("secret");

        Authentication authentication = new UsernamePasswordAuthenticationToken("user@example.com", "secret");
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(authentication);
        when(jwtService.generateToken(authentication)).thenReturn("jwt-token");

        ResponseEntity<?> response = authController.login(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        LoginResponse body = assertInstanceOf(LoginResponse.class, response.getBody());
        assertEquals("jwt-token", body.getToken());
    }

    @Test
    @DisplayName("Logowanie zwraca 401 przy niepoprawnych danych")
    void loginReturnsUnauthorizedWhenCredentialsAreInvalid() {
        LoginRequest request = new LoginRequest();
        request.setEmail("user@example.com");
        request.setPassword("bad");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("bad credentials"));

        ResponseEntity<?> response = authController.login(request);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        AuthErrorResponse body = assertInstanceOf(AuthErrorResponse.class, response.getBody());
        assertEquals("Invalid email or password", body.getMessage());
    }

    @Test
    @DisplayName("Rejestracja zwraca konflikt gdy email istnieje")
    void registerReturnsConflictWhenEmailAlreadyExists() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("user@example.com");
        request.setPassword("secret");
        request.setFirstName("Jan");
        request.setLastName("Kowalski");

        when(appUserRepository.existsByEmail("user@example.com")).thenReturn(true);

        ResponseEntity<?> response = authController.register(request);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        AuthErrorResponse body = assertInstanceOf(AuthErrorResponse.class, response.getBody());
        assertEquals("Email already exists", body.getMessage());
        verify(appUserRepository, never()).save(any(AppUser.class));
    }

    @Test
    @DisplayName("Rejestracja zwraca 201 i token gdy auto-login udany")
    void registerReturnsCreatedWithTokenWhenAutoLoginSucceeds() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail(" user@example.com ");
        request.setPassword("secret");
        request.setFirstName("Jan");
        request.setLastName("Kowalski");
        request.setPhoneNumber("123456789");

        when(appUserRepository.existsByEmail("user@example.com")).thenReturn(false);
        when(passwordEncoder.encode("secret")).thenReturn("hashed-secret");

        AppUser savedUser = new AppUser();
        savedUser.setEmail("user@example.com");
        when(appUserRepository.save(any(AppUser.class))).thenReturn(savedUser);

        Authentication authentication = new UsernamePasswordAuthenticationToken("user@example.com", "secret");
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(authentication);
        when(jwtService.generateToken(authentication)).thenReturn("token-123");

        ResponseEntity<?> response = authController.register(request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        LoginResponse body = assertInstanceOf(LoginResponse.class, response.getBody());
        assertEquals("token-123", body.getToken());
    }
}
