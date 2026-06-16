package biali.fitmanager.backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import biali.fitmanager.backend.dto.AuthErrorResponse;
import biali.fitmanager.backend.dto.LoginRequest;
import biali.fitmanager.backend.dto.LoginResponse;
import biali.fitmanager.backend.security.JwtService;
import biali.fitmanager.backend.dto.RegisterRequest;
import biali.fitmanager.backend.model.AppUser;
import biali.fitmanager.backend.repository.AppUserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import biali.fitmanager.backend.validation.InputValidator;

/**
 * Endpointy rejestracji i logowania użytkowników.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService; 
    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthController(AuthenticationManager authenticationManager, JwtService jwtService,
                          AppUserRepository appUserRepository, PasswordEncoder passwordEncoder) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Logowanie użytkownika na podstawie emaila i hasła.
     *
     * @param loginRequest dane logowania (email, password)
     * @return 200 z {@link LoginResponse} (token JWT), 400 z {@link AuthErrorResponse}, 401 przy błędnych danych
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest) {
        String validationError = InputValidator.validateLogin(loginRequest);
        if (validationError != null) {
            return ResponseEntity.badRequest().body(new AuthErrorResponse(validationError));
        }

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginRequest.getEmail(),
                            loginRequest.getPassword()
                    )
            );

            String token = jwtService.generateToken(authentication);
            return ResponseEntity.ok(new LoginResponse(token));
        } catch (AuthenticationException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new AuthErrorResponse("Invalid email or password"));
        }
    }

    /**
     * Rejestracja nowego klienta i automatyczne logowanie.
     *
     * @param request dane rejestracyjne (email, hasło, imię, nazwisko, telefon)
     * @return 201 z {@link LoginResponse} (token JWT), 400 przy błędach walidacji, 409 gdy email już istnieje
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        String validationError = InputValidator.validateRegister(request);
        if (validationError != null) {
            return ResponseEntity.badRequest().body(new AuthErrorResponse(validationError));
        }

        String email = request.getEmail().trim().toLowerCase();
        if (appUserRepository.existsByEmail(email)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new AuthErrorResponse("Email already exists"));
        }

        AppUser user = new AppUser();
        user.setEmail(email);
        user.setFirstName(request.getFirstName().trim());
        user.setLastName(request.getLastName().trim());
        if (request.getPhoneNumber() != null) user.setPhoneNumber(request.getPhoneNumber().trim());
        user.setRole("CLIENT");
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));

        AppUser saved = appUserRepository.save(user);

        // authenticate and return token
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getEmail(), request.getPassword()
                    )
            );
            String token = jwtService.generateToken(authentication);
            return ResponseEntity.status(HttpStatus.CREATED).body(new LoginResponse(token));
        } catch (AuthenticationException ex) {
            // registration succeeded but auto-login failed - still return created
            return ResponseEntity.status(HttpStatus.CREATED).body(new LoginResponse(""));
        }
    }
}