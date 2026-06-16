package biali.fitmanager.backend.validation;

import java.math.BigDecimal;
import java.util.Set;
import java.util.regex.Pattern;

import biali.fitmanager.backend.dto.LoginRequest;
import biali.fitmanager.backend.dto.RegisterRequest;
import biali.fitmanager.backend.dto.UserUpsertRequest;

/**
 * Walidacja danych wejściowych API (email, hasło, telefon, kwoty itp.).
 */
public final class InputValidator {

    public static final int MIN_PASSWORD_LENGTH = 8;
    public static final int MAX_PASSWORD_LENGTH = 128;
    public static final int MAX_NAME_LENGTH = 100;
    public static final int MAX_EMAIL_LENGTH = 255;
    public static final BigDecimal MAX_TOP_UP_AMOUNT = new BigDecimal("1000");

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );
    private static final Pattern PHONE_PATTERN = Pattern.compile("^[0-9]{9}$");
    private static final Set<String> ALLOWED_ROLES = Set.of("ADMIN", "TRAINER", "CLIENT");

    private InputValidator() {
    }

    /**
     * Waliduje żądanie logowania.
     *
     * @param request dane logowania (email, hasło)
     * @return komunikat błędu lub null gdy dane poprawne
     */
    public static String validateLogin(LoginRequest request) {
        if (request == null) {
            return "Invalid login payload";
        }
        if (isBlank(request.getEmail())) {
            return "Email is required";
        }
        if (!isValidEmail(request.getEmail())) {
            return "Invalid email format";
        }
        if (isBlank(request.getPassword())) {
            return "Password is required";
        }
        return null;
    }

    /**
     * Waliduje żądanie rejestracji nowego klienta.
     *
     * @param request dane rejestracyjne
     * @return komunikat błędu lub null gdy dane poprawne
     */
    public static String validateRegister(RegisterRequest request) {
        if (request == null) {
            return "Invalid registration payload";
        }
        String firstNameError = validateName(request.getFirstName(), "First name");
        if (firstNameError != null) {
            return firstNameError;
        }
        String lastNameError = validateName(request.getLastName(), "Last name");
        if (lastNameError != null) {
            return lastNameError;
        }
        String emailError = validateEmail(request.getEmail());
        if (emailError != null) {
            return emailError;
        }
        String passwordError = validatePassword(request.getPassword());
        if (passwordError != null) {
            return passwordError;
        }
        if (request.getPhoneNumber() != null && !request.getPhoneNumber().isBlank()) {
            String phoneError = validatePhone(request.getPhoneNumber());
            if (phoneError != null) {
                return phoneError;
            }
        }
        return null;
    }

    /**
     * Waliduje zmianę hasła użytkownika.
     *
     * @param currentPassword aktualne hasło
     * @param newPassword nowe hasło
     * @return komunikat błędu lub null gdy dane poprawne
     */
    public static String validatePasswordChange(String currentPassword, String newPassword) {
        if (isBlank(currentPassword)) {
            return "Current password is required";
        }
        String newPasswordError = validatePassword(newPassword);
        if (newPasswordError != null) {
            return newPasswordError;
        }
        if (currentPassword.equals(newPassword)) {
            return "New password must be different from current password";
        }
        return null;
    }

    /**
     * Waliduje tworzenie nowego użytkownika.
     *
     * @param request dane użytkownika do utworzenia
     * @return komunikat błędu lub null gdy dane poprawne
     */
    public static String validateUserCreate(UserUpsertRequest request) {
        if (request == null) {
            return "Invalid user payload";
        }
        String emailError = validateEmail(request.getEmail());
        if (emailError != null) {
            return emailError;
        }
        String passwordError = validatePassword(request.getPassword());
        if (passwordError != null) {
            return passwordError;
        }
        String firstNameError = validateName(request.getFirstName(), "First name");
        if (firstNameError != null) {
            return firstNameError;
        }
        String lastNameError = validateName(request.getLastName(), "Last name");
        if (lastNameError != null) {
            return lastNameError;
        }
        String roleError = validateRole(request.getRole(), true);
        if (roleError != null) {
            return roleError;
        }
        if (request.getPhoneNumber() != null && !request.getPhoneNumber().isBlank()) {
            String phoneError = validatePhone(request.getPhoneNumber());
            if (phoneError != null) {
                return phoneError;
            }
        }
        return null;
    }

    /**
     * Waliduje aktualizację istniejącego użytkownika.
     *
     * @param request pola do aktualizacji
     * @return komunikat błędu lub null gdy dane poprawne
     */
    public static String validateUserUpdate(UserUpsertRequest request) {
        if (request == null) {
            return "Invalid user payload";
        }
        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            String emailError = validateEmail(request.getEmail());
            if (emailError != null) {
                return emailError;
            }
        }
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            String passwordError = validatePassword(request.getPassword());
            if (passwordError != null) {
                return passwordError;
            }
        }
        if (request.getFirstName() != null) {
            String firstNameError = validateName(request.getFirstName(), "First name");
            if (firstNameError != null) {
                return firstNameError;
            }
        }
        if (request.getLastName() != null) {
            String lastNameError = validateName(request.getLastName(), "Last name");
            if (lastNameError != null) {
                return lastNameError;
            }
        }
        String roleError = validateRole(request.getRole(), false);
        if (roleError != null) {
            return roleError;
        }
        if (request.getPhoneNumber() != null && !request.getPhoneNumber().isBlank()) {
            String phoneError = validatePhone(request.getPhoneNumber());
            if (phoneError != null) {
                return phoneError;
            }
        }
        return null;
    }

    /**
     * Waliduje aktualizację profilu użytkownika.
     *
     * @param request imię, nazwisko, telefon
     * @return komunikat błędu lub null gdy dane poprawne
     */
    public static String validateProfileUpdate(UserUpsertRequest request) {
        if (request == null) {
            return "Invalid profile payload";
        }
        String firstNameError = validateName(request.getFirstName(), "First name");
        if (firstNameError != null) {
            return firstNameError;
        }
        String lastNameError = validateName(request.getLastName(), "Last name");
        if (lastNameError != null) {
            return lastNameError;
        }
        if (request.getPhoneNumber() != null && !request.getPhoneNumber().isBlank()) {
            String phoneError = validatePhone(request.getPhoneNumber());
            if (phoneError != null) {
                return phoneError;
            }
        }
        return null;
    }

    /**
     * Waliduje typ karnetu.
     *
     * @param name nazwa karnetu
     * @param price cena
     * @param durationDays czas trwania w dniach
     * @return komunikat błędu lub null gdy dane poprawne
     */
    public static String validateMembershipType(String name, BigDecimal price, Integer durationDays) {
        if (isBlank(name)) {
            return "Membership name is required";
        }
        if (name.trim().length() > 100) {
            return "Membership name is too long";
        }
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            return "Price must be greater than zero";
        }
        if (durationDays == null || durationDays < 1) {
            return "Duration must be at least 1 day";
        }
        return null;
    }

    /**
     * Waliduje format i długość adresu email.
     *
     * @param email adres email
     * @return komunikat błędu lub null gdy email poprawny
     */
    public static String validateEmail(String email) {
        if (isBlank(email)) {
            return "Email is required";
        }
        String trimmed = email.trim();
        if (trimmed.length() > MAX_EMAIL_LENGTH) {
            return "Email is too long";
        }
        if (!isValidEmail(trimmed)) {
            return "Invalid email format";
        }
        return null;
    }

    /**
     * Waliduje długość hasła.
     *
     * @param password hasło do sprawdzenia
     * @return komunikat błędu lub null gdy hasło poprawne
     */
    public static String validatePassword(String password) {
        if (isBlank(password)) {
            return "Password is required";
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            return "Password must be at least " + MIN_PASSWORD_LENGTH + " characters";
        }
        if (password.length() > MAX_PASSWORD_LENGTH) {
            return "Password is too long";
        }
        return null;
    }

    /**
     * Waliduje imię lub nazwisko.
     *
     * @param name wartość pola
     * @param fieldLabel etykieta pola (np. "First name")
     * @return komunikat błędu lub null gdy wartość poprawna
     */
    public static String validateName(String name, String fieldLabel) {
        if (isBlank(name)) {
            return fieldLabel + " is required";
        }
        if (name.trim().length() > MAX_NAME_LENGTH) {
            return fieldLabel + " is too long";
        }
        return null;
    }

    /**
     * Waliduje numer telefonu (dokładnie 9 cyfr).
     *
     * @param phone numer telefonu
     * @return komunikat błędu lub null gdy numer poprawny
     */
    public static String validatePhone(String phone) {
        String trimmed = phone.trim();
        if (!PHONE_PATTERN.matcher(trimmed).matches()) {
            return "Phone number must contain exactly 9 digits";
        }
        return null;
    }

    /**
     * Waliduje wagę ciała lub ciężar ćwiczenia.
     *
     * @param weight wartość wagi
     * @return komunikat błędu lub null gdy wartość nieujemna
     */
    public static String validateWeight(Double weight) {
        if (weight == null) {
            return "Weight is required";
        }
        if (weight < 0) {
            return "Weight cannot be negative";
        }
        return null;
    }

    /**
     * Waliduje kwotę doładowania konta.
     *
     * @param amount kwota doładowania (maks. 1000 PLN)
     * @return komunikat błędu lub null gdy kwota poprawna
     */
    public static String validateTopUpAmount(BigDecimal amount) {
        if (amount == null) {
            return "Invalid amount";
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return "Amount must be greater than zero";
        }
        if (amount.compareTo(MAX_TOP_UP_AMOUNT) > 0) {
            return "Maximum top-up amount is 1000";
        }
        return null;
    }

    private static String validateRole(String role, boolean required) {
        if (role == null || role.isBlank()) {
            return required ? "Role is required" : null;
        }
        if (!ALLOWED_ROLES.contains(role.trim().toUpperCase())) {
            return "Invalid role";
        }
        return null;
    }

    private static boolean isValidEmail(String email) {
        return EMAIL_PATTERN.matcher(email.trim()).matches();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
