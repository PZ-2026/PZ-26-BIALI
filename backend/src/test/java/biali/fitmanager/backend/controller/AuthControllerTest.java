package biali.fitmanager.backend.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

/**
 * Testy jednostkowe {@link AuthController}: logowanie i rejestracja.
 */
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

    /**
     * Sprawdza poprawne logowanie z prawidłowymi danymi.
     *
     * @param request LoginRequest z poprawnym emailem i hasłem
     * @return status 200, {@link LoginResponse} z tokenem JWT, wywołanie AuthenticationManager
     */
    @Test
    @DisplayName("Logowanie zwraca token przy poprawnych danych")
    void loginReturnsTokenWhenCredentialsAreValid() {
        LoginRequest request = new LoginRequest();
        request.setEmail("user@example.com");
        request.setPassword("secret12");

        Authentication authentication = new UsernamePasswordAuthenticationToken("user@example.com", "secret12");
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(authentication);
        when(jwtService.generateToken(authentication)).thenReturn("jwt-token");

        ResponseEntity<?> response = authController.login(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        LoginResponse body = assertInstanceOf(LoginResponse.class, response.getBody());
        assertNotNull(body);
        assertEquals("jwt-token", body.getToken());
        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
    }

    /**
     * Weryfikuje odrzucenie logowania z niepoprawnym formatem email.
     *
     * @param request LoginRequest z emailem "not-an-email"
     * @return status 400, {@link AuthErrorResponse}, brak wywołania AuthenticationManager
     */
    @Test
    @DisplayName("Logowanie zwraca 400 dla niepoprawnego emaila")
    void loginReturnsBadRequestForInvalidEmail() {
        LoginRequest request = new LoginRequest();
        request.setEmail("not-an-email");
        request.setPassword("secret12");

        ResponseEntity<?> response = authController.login(request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(assertInstanceOf(AuthErrorResponse.class, response.getBody()).getMessage()
                .contains("email"));
        verify(authenticationManager, never()).authenticate(any());
    }

    /**
     * Potwierdza odrzucenie logowania przy błędnych danych uwierzytelniających.
     *
     * @param request LoginRequest z poprawnym emailem i złym hasłem
     * @return status 401, {@link AuthErrorResponse} z komunikatem "Invalid email or password"
     */
    @Test
    @DisplayName("Logowanie zwraca 401 przy niepoprawnych danych")
    void loginReturnsUnauthorizedWhenCredentialsAreInvalid() {
        LoginRequest request = new LoginRequest();
        request.setEmail("user@example.com");
        request.setPassword("badpass1");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("bad credentials"));

        ResponseEntity<?> response = authController.login(request);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        AuthErrorResponse body = assertInstanceOf(AuthErrorResponse.class, response.getBody());
        assertEquals("Invalid email or password", body.getMessage());
    }

    /**
     * Sprawdza walidację rejestracji przed zapisem użytkownika.
     *
     * @param request RegisterRequest z hasłem krótszym niż 8 znaków
     * @return status 400, brak zapisu w AppUserRepository
     */
    @Test
    @DisplayName("Rejestracja zwraca 400 dla zbyt krotkiego hasla")
    void registerReturnsBadRequestForShortPassword() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("user@example.com");
        request.setPassword("short");
        request.setFirstName("Jan");
        request.setLastName("Kowalski");

        ResponseEntity<?> response = authController.register(request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(appUserRepository, never()).save(any(AppUser.class));
        verify(appUserRepository, never()).existsByEmail(any());
    }

    /**
     * Weryfikuje konflikt gdy email jest już zajęty.
     *
     * @param request RegisterRequest z istniejącym emailem
     * @return status 409, {@link AuthErrorResponse} "Email already exists", brak zapisu użytkownika
     */
    @Test
    @DisplayName("Rejestracja zwraca konflikt gdy email istnieje")
    void registerReturnsConflictWhenEmailAlreadyExists() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("user@example.com");
        request.setPassword("secret12");
        request.setFirstName("Jan");
        request.setLastName("Kowalski");

        when(appUserRepository.existsByEmail("user@example.com")).thenReturn(true);

        ResponseEntity<?> response = authController.register(request);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        AuthErrorResponse body = assertInstanceOf(AuthErrorResponse.class, response.getBody());
        assertEquals("Email already exists", body.getMessage());
        verify(appUserRepository, never()).save(any(AppUser.class));
    }

    /**
     * Potwierdza poprawną rejestrację z auto-logowaniem i zwrotem tokenu JWT.
     *
     * @param request RegisterRequest z poprawnymi danymi klienta
     * @return status 201, {@link LoginResponse} z tokenem JWT, zapis użytkownika z rolą CLIENT
     */
    @Test
    @DisplayName("Rejestracja zwraca 201 i token gdy auto-login udany")
    void registerReturnsCreatedWithTokenWhenAutoLoginSucceeds() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail(" user@example.com ");
        request.setPassword("secret12");
        request.setFirstName("Jan");
        request.setLastName("Kowalski");
        request.setPhoneNumber("123456789");

        when(appUserRepository.existsByEmail("user@example.com")).thenReturn(false);
        when(passwordEncoder.encode("secret12")).thenReturn("hashed-secret");

        AppUser savedUser = new AppUser();
        savedUser.setEmail("user@example.com");
        when(appUserRepository.save(any(AppUser.class))).thenReturn(savedUser);

        Authentication authentication = new UsernamePasswordAuthenticationToken("user@example.com", "secret12");
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(authentication);
        when(jwtService.generateToken(authentication)).thenReturn("token-123");

        ResponseEntity<?> response = authController.register(request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        LoginResponse body = assertInstanceOf(LoginResponse.class, response.getBody());
        assertNotNull(body.getToken());
        assertEquals("token-123", body.getToken());
        verify(passwordEncoder).encode("secret12");
    }
}
