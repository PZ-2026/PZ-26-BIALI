package biali.fitmanager.backend.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import biali.fitmanager.backend.controller.ProfileController.ChangePasswordRequest;
import biali.fitmanager.backend.dto.AuthErrorResponse;
import biali.fitmanager.backend.dto.MeResponse;
import biali.fitmanager.backend.model.AppUser;
import biali.fitmanager.backend.model.TrainerRental;
import biali.fitmanager.backend.repository.AppUserRepository;
import biali.fitmanager.backend.repository.TrainerRentalRepository;

/**
 * Testy jednostkowe {@link ProfileController}: profil i zmiana hasła.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Testy kontrolera profilu uzytkownika")
class ProfileControllerTest {

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private TrainerRentalRepository trainerRentalRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private Authentication authentication;

    private ProfileController profileController;

    @BeforeEach
    void setUp() {
        profileController = new ProfileController(appUserRepository, trainerRentalRepository, passwordEncoder);
    }

    /**
     * Weryfikuje, że endpoint /api/me zwraca dane zalogowanego użytkownika z aktywnym trenerem.
     *
     * @param authentication token JWT klienta (client@example.com)
     * @return status 200, {@link MeResponse} z danymi profilu i trainerId aktywnego wynajmu
     */
    @Test
    @DisplayName("Pobranie profilu zwraca MeResponse dla istniejacego uzytkownika")
    void getCurrentUserReturnsProfileForExistingUser() {
        AppUser user = buildUser(3, "client@example.com", "CLIENT", "Anna", "Nowak");
        TrainerRental rental = new TrainerRental();
        rental.setTrainerId(7);
        rental.setStatus("ACTIVE");
        rental.setEndDate(LocalDate.now().plusMonths(1));

        when(authentication.getName()).thenReturn("client@example.com");
        when(appUserRepository.findByEmail("client@example.com")).thenReturn(Optional.of(user));
        when(trainerRentalRepository.findAllByClientId(3)).thenReturn(List.of(rental));

        ResponseEntity<?> response = profileController.getCurrentUser(authentication);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        MeResponse body = assertInstanceOf(MeResponse.class, response.getBody());
        assertNotNull(body);
        assertEquals("client@example.com", body.email());
        assertEquals("Anna Nowak", body.name());
        assertEquals(Integer.valueOf(7), body.trainerId());
        assertTrue(body.trainerEndDate().contains(LocalDate.now().plusMonths(1).toString()));
    }

    /**
     * Sprawdza, że brak użytkownika w repozytorium skutkuje odpowiedzią 404.
     *
     * @param authentication token JWT nieistniejącego użytkownika
     * @return status 404, {@link AuthErrorResponse} "User not found"
     */
    @Test
    @DisplayName("Pobranie profilu zwraca 404 gdy uzytkownik nie istnieje")
    void getCurrentUserReturnsNotFoundWhenUserMissing() {
        when(authentication.getName()).thenReturn("missing@example.com");
        when(appUserRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        ResponseEntity<?> response = profileController.getCurrentUser(authentication);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        AuthErrorResponse body = assertInstanceOf(AuthErrorResponse.class, response.getBody());
        assertEquals("User not found", body.getMessage());
    }

    /**
     * Weryfikuje walidację payloadu zmiany hasła przed dotknięciem repozytorium.
     *
     * @param request ChangePasswordRequest z nowym hasłem krótszym niż 8 znaków
     * @return status 400, {@link AuthErrorResponse}, brak zapisu użytkownika
     */
    @Test
    @DisplayName("Zmiana hasla zwraca 400 dla zbyt krotkiego nowego hasla")
    void changePasswordReturnsBadRequestForShortNewPassword() {
        ChangePasswordRequest request = new ChangePasswordRequest("OldPass12", "short");

        ResponseEntity<?> response = profileController.changePassword(authentication, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        AuthErrorResponse body = assertInstanceOf(AuthErrorResponse.class, response.getBody());
        assertTrue(body.getMessage().contains("8"));
        verify(appUserRepository, never()).save(any(AppUser.class));
    }

    /**
     * Sprawdza odrzucenie zmiany hasła, gdy aktualne hasło jest niepoprawne.
     *
     * @param authentication token JWT użytkownika
     * @param request ChangePasswordRequest z błędnym aktualnym hasłem
     * @return status 400, {@link AuthErrorResponse} "Current password is incorrect"
     */
    @Test
    @DisplayName("Zmiana hasla zwraca 400 gdy aktualne haslo jest bledne")
    void changePasswordReturnsBadRequestWhenCurrentPasswordIsWrong() {
        AppUser user = buildUser(1, "user@example.com", "CLIENT", "Jan", "Kowalski");
        user.setPasswordHash("hashed-old");
        ChangePasswordRequest request = new ChangePasswordRequest("WrongPass1", "NewPass123");

        when(authentication.getName()).thenReturn("user@example.com");
        when(appUserRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("WrongPass1", "hashed-old")).thenReturn(false);

        ResponseEntity<?> response = profileController.changePassword(authentication, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Current password is incorrect",
                assertInstanceOf(AuthErrorResponse.class, response.getBody()).getMessage());
        verify(appUserRepository, never()).save(any(AppUser.class));
    }

    /**
     * Potwierdza poprawną zmianę hasła i zapis nowego hashu w bazie.
     *
     * @param authentication token JWT użytkownika
     * @param request ChangePasswordRequest z poprawnym aktualnym i nowym hasłem
     * @return status 200, zapis użytkownika z nowym passwordHash
     */
    @Test
    @DisplayName("Zmiana hasla zapisuje nowy hash gdy dane sa poprawne")
    void changePasswordUpdatesHashWhenPayloadIsValid() {
        AppUser user = buildUser(1, "user@example.com", "CLIENT", "Jan", "Kowalski");
        user.setPasswordHash("hashed-old");
        ChangePasswordRequest request = new ChangePasswordRequest("OldPass12", "NewPass123");

        when(authentication.getName()).thenReturn("user@example.com");
        when(appUserRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("OldPass12", "hashed-old")).thenReturn(true);
        when(passwordEncoder.encode("NewPass123")).thenReturn("hashed-new");

        ResponseEntity<?> response = profileController.changePassword(authentication, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNull(response.getBody());

        ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        verify(appUserRepository).save(captor.capture());
        assertEquals("hashed-new", captor.getValue().getPasswordHash());
    }

    private AppUser buildUser(int id, String email, String role, String firstName, String lastName) {
        AppUser user = new AppUser();
        ReflectionTestUtils.setField(user, "id", id);
        user.setEmail(email);
        user.setRole(role);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setAccountBalance(BigDecimal.ZERO);
        return user;
    }
}
