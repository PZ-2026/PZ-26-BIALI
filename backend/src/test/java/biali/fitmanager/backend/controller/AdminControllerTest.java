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
import org.springframework.security.crypto.password.PasswordEncoder;

import biali.fitmanager.backend.dto.AuthErrorResponse;
import biali.fitmanager.backend.dto.LoginRequest;
import biali.fitmanager.backend.dto.LoginResponse;
import biali.fitmanager.backend.dto.RegisterRequest;
import biali.fitmanager.backend.dto.UserResponse;
import biali.fitmanager.backend.dto.UserUpsertRequest;
import biali.fitmanager.backend.model.AppUser;
import biali.fitmanager.backend.repository.AppUserRepository;
import biali.fitmanager.backend.repository.PaymentRepository;
import biali.fitmanager.backend.repository.TrainerClientRepository;
import biali.fitmanager.backend.service.PdfReportService;

/**
 * Testy jednostkowe {@link AdminController}: tworzenie użytkowników i trenerów.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Testy kontrolera administratora")
class AdminControllerTest {

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private TrainerClientRepository trainerClientRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private PdfReportService pdfReportService;

    @Mock
    private PaymentRepository paymentRepository;

    private AdminController adminController;

    @BeforeEach
    void setUp() {
        adminController = new AdminController(
                appUserRepository,
                trainerClientRepository,
                passwordEncoder,
                pdfReportService,
                paymentRepository
        );
    }

    /**
     * Sprawdza odrzucenie tworzenia użytkownika z hasłem krótszym niż 8 znaków.
     *
     * @param request UserUpsertRequest z hasłem "short"
     * @return status 400, {@link AuthErrorResponse}, brak zapisu użytkownika
     */
    @Test
    @DisplayName("Tworzenie uzytkownika zwraca 400 dla krotkiego hasla")
    void createUserReturnsBadRequestForShortPassword() {
        UserUpsertRequest request = new UserUpsertRequest();
        request.setEmail("admin-created@example.com");
        request.setPassword("short");
        request.setFirstName("Jan");
        request.setLastName("Kowalski");
        request.setRole("CLIENT");

        ResponseEntity<?> response = adminController.createUser(request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        AuthErrorResponse body = assertInstanceOf(AuthErrorResponse.class, response.getBody());
        assertTrue(body.getMessage().contains("8"));
        verify(appUserRepository, never()).save(any(AppUser.class));
    }

    /**
     * Weryfikuje konflikt gdy email już istnieje w systemie.
     *
     * @param request UserUpsertRequest z zajętym emailem
     * @return status 409, {@link AuthErrorResponse} "Email already exists", brak zapisu
     */
    @Test
    @DisplayName("Tworzenie uzytkownika zwraca 409 gdy email istnieje")
    void createUserReturnsConflictWhenEmailExists() {
        UserUpsertRequest request = validCreateRequest();

        when(appUserRepository.existsByEmail("newadmin@example.com")).thenReturn(true);

        ResponseEntity<?> response = adminController.createUser(request);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("Email already exists",
                assertInstanceOf(AuthErrorResponse.class, response.getBody()).getMessage());
        verify(appUserRepository, never()).save(any(AppUser.class));
    }

    /**
     * Potwierdza poprawne utworzenie trenera z wymuszoną rolą TRAINER.
     *
     * @param request UserUpsertRequest z rolą CLIENT (nadpisywaną na TRAINER)
     * @return status 201, {@link UserResponse} z rolą TRAINER
     */
    @Test
    @DisplayName("Tworzenie trenera wymusza role TRAINER")
    void createTrainerForcesTrainerRole() {
        UserUpsertRequest request = validCreateRequest();
        request.setRole("CLIENT");

        when(appUserRepository.existsByEmail("newadmin@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Password1")).thenReturn("hashed");
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(invocation -> {
            AppUser saved = invocation.getArgument(0);
            org.springframework.test.util.ReflectionTestUtils.setField(saved, "id", 20);
            org.springframework.test.util.ReflectionTestUtils.setField(saved, "createdAt", java.time.LocalDateTime.now());
            return saved;
        });

        ResponseEntity<?> response = adminController.createTrainer(request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        UserResponse body = assertInstanceOf(UserResponse.class, response.getBody());
        assertNotNull(body);
        assertEquals("TRAINER", body.role());
    }

    private UserUpsertRequest validCreateRequest() {
        UserUpsertRequest request = new UserUpsertRequest();
        request.setEmail("newadmin@example.com");
        request.setPassword("Password1");
        request.setFirstName("Jan");
        request.setLastName("Kowalski");
        request.setRole("TRAINER");
        request.setPhoneNumber("600700800");
        return request;
    }
}
