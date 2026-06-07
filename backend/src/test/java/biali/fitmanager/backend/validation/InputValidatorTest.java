package biali.fitmanager.backend.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import biali.fitmanager.backend.dto.LoginRequest;
import biali.fitmanager.backend.dto.RegisterRequest;
import biali.fitmanager.backend.dto.UserUpsertRequest;

/**
 * Testy jednostkowe {@link InputValidator}: walidacja danych wejściowych API.
 */
@DisplayName("Testy walidacji danych wejsciowych")
class InputValidatorTest {

    /**
     * Sprawdza wymaganie niepustego adresu email przy logowaniu.
     *
     * @param request LoginRequest z pustym emailem
     * @return komunikat błędu "Email is required"
     */
    @Test
    @DisplayName("Logowanie odrzuca pusty email")
    void loginRejectsBlankEmail() {
        LoginRequest request = new LoginRequest();
        request.setEmail("");
        request.setPassword("password123");

        String error = InputValidator.validateLogin(request);

        assertNotNull(error);
        assertEquals("Email is required", error);
    }

    /**
     * Weryfikuje odrzucenie niepoprawnego formatu email.
     *
     * @param request LoginRequest z emailem "not-an-email"
     * @return komunikat błędu zawierający "email"
     */
    @Test
    @DisplayName("Logowanie odrzuca niepoprawny email")
    void loginRejectsInvalidEmail() {
        LoginRequest request = new LoginRequest();
        request.setEmail("not-an-email");
        request.setPassword("password123");

        String error = InputValidator.validateLogin(request);

        assertNotNull(error);
        assertTrue(error.contains("email"));
    }

    /**
     * Potwierdza minimalną długość hasła przy rejestracji.
     *
     * @param request RegisterRequest z hasłem "short"
     * @return komunikat błędu "Password must be at least 8 characters"
     */
    @Test
    @DisplayName("Rejestracja odrzuca zbyt krotkie haslo")
    void registerRejectsShortPassword() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("user@example.com");
        request.setPassword("short");
        request.setFirstName("Jan");
        request.setLastName("Kowalski");

        String error = InputValidator.validateRegister(request);

        assertNotNull(error);
        assertEquals("Password must be at least 8 characters", error);
    }

    /**
     * Sprawdza akceptację poprawnego payloadu rejestracji.
     *
     * @param request RegisterRequest z poprawnymi danymi
     * @return null (brak błędu walidacji)
     */
    @Test
    @DisplayName("Rejestracja akceptuje poprawne dane")
    void registerAcceptsValidPayload() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("user@example.com");
        request.setPassword("password123");
        request.setFirstName("Jan");
        request.setLastName("Kowalski");
        request.setPhoneNumber("600700800");

        assertNull(InputValidator.validateRegister(request));
    }

    /**
     * Weryfikuje wymaganie dokładnie 9 cyfr w numerze telefonu.
     *
     * @param phone numer telefonu do walidacji
     * @return null dla "600700800", komunikat błędu dla złej długości
     */
    @Test
    @DisplayName("Telefon musi miec 9 cyfr")
    void phoneMustHaveNineDigits() {
        assertNotNull(InputValidator.validatePhone("12345"));
        assertNotNull(InputValidator.validatePhone("1234567890"));
        assertNull(InputValidator.validatePhone("600700800"));
    }

    /**
     * Sprawdza regułę ciężaru: zero dozwolone, wartości ujemne zabronione.
     *
     * @param weight wartość ciężaru do walidacji
     * @return "Weight cannot be negative" dla -1.0, null dla 0.0 i 80.5
     */
    @Test
    @DisplayName("Ciezar nie moze byc ujemny")
    void weightCannotBeNegative() {
        assertEquals("Weight cannot be negative", InputValidator.validateWeight(-1.0));
        assertNull(InputValidator.validateWeight(0.0));
        assertNull(InputValidator.validateWeight(80.5));
    }

    /**
     * Weryfikuje limit doładowania portfela do 1000 PLN.
     *
     * @param amount kwota doładowania
     * @return null dla 1000, komunikat błędu dla kwot ujemnych i powyżej limitu
     */
    @Test
    @DisplayName("Doładowanie ma limit 1000 zl")
    void topUpHasUpperLimit() {
        assertEquals("Maximum top-up amount is 1000",
                InputValidator.validateTopUpAmount(new BigDecimal("1000.01")));
        assertNull(InputValidator.validateTopUpAmount(new BigDecimal("1000")));
        assertEquals("Amount must be greater than zero",
                InputValidator.validateTopUpAmount(new BigDecimal("-10")));
    }

    /**
     * Sprawdza walidację zmiany hasła: nowe hasło musi różnić się od aktualnego.
     *
     * @param currentPassword aktualne hasło
     * @param newPassword nowe hasło (identyczne z aktualnym)
     * @return komunikat błędu zawierający "different"
     */
    @Test
    @DisplayName("Zmiana hasla odrzuca identyczne hasla")
    void passwordChangeRejectsSamePassword() {
        String error = InputValidator.validatePasswordChange("SamePass1", "SamePass1");

        assertNotNull(error);
        assertTrue(error.contains("different"));
    }

    /**
     * Weryfikuje walidację tworzenia użytkownika z niepoprawną rolą.
     *
     * @param request UserUpsertRequest z rolą "SUPERUSER"
     * @return komunikat błędu "Invalid role"
     */
    @Test
    @DisplayName("Tworzenie uzytkownika odrzuca niepoprawna role")
    void userCreateRejectsInvalidRole() {
        UserUpsertRequest request = new UserUpsertRequest();
        request.setEmail("user@example.com");
        request.setPassword("Password1");
        request.setFirstName("Jan");
        request.setLastName("Kowalski");
        request.setRole("SUPERUSER");

        String error = InputValidator.validateUserCreate(request);

        assertNotNull(error);
        assertEquals("Invalid role", error);
    }
}
