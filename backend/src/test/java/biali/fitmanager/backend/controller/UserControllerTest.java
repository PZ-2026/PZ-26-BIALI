package biali.fitmanager.backend.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
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

import biali.fitmanager.backend.dto.AuthErrorResponse;
import biali.fitmanager.backend.dto.TopUpRequest;
import biali.fitmanager.backend.dto.UserResponse;
import biali.fitmanager.backend.dto.UserUpsertRequest;
import biali.fitmanager.backend.model.AppUser;
import biali.fitmanager.backend.repository.AppUserRepository;
import biali.fitmanager.backend.repository.MembershipRepository;
import biali.fitmanager.backend.repository.MembershipTypeRepository;
import biali.fitmanager.backend.repository.PaymentRepository;

/**
 * Testy jednostkowe {@link UserController}: CRUD użytkowników i doładowanie salda.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Testy kontrolera uzytkownikow")
class UserControllerTest {

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private MembershipTypeRepository membershipTypeRepository;

    @Mock
    private MembershipRepository membershipRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private Authentication authentication;

    private UserController userController;

    @BeforeEach
    void setUp() {
        userController = new UserController(
                appUserRepository,
                passwordEncoder,
                membershipTypeRepository,
                membershipRepository,
                paymentRepository
        );
    }

    /**
     * Sprawdza odrzucenie utworzenia użytkownika z niepoprawnym formatem telefonu.
     *
     * @param request UserUpsertRequest z telefonem "12345" (za krótki)
     * @return status 400, {@link AuthErrorResponse}, brak zapisu użytkownika
     */
    @Test
    @DisplayName("Tworzenie uzytkownika zwraca 400 dla niepoprawnego telefonu")
    void createUserReturnsBadRequestForInvalidPhone() {
        UserUpsertRequest request = validCreateRequest();
        request.setPhoneNumber("12345");

        ResponseEntity<?> response = userController.createUser(request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        AuthErrorResponse body = assertInstanceOf(AuthErrorResponse.class, response.getBody());
        assertTrue(body.getMessage().contains("9 digits"));
        verify(appUserRepository, never()).save(any(AppUser.class));
    }

    /**
     * Weryfikuje poprawne utworzenie użytkownika po przejściu walidacji.
     *
     * @param request UserUpsertRequest z poprawnymi danymi klienta
     * @return status 201, {@link UserResponse} z emailem newuser@example.com
     */
    @Test
    @DisplayName("Tworzenie uzytkownika zwraca 201 dla poprawnych danych")
    void createUserReturnsCreatedForValidPayload() {
        UserUpsertRequest request = validCreateRequest();

        when(appUserRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(passwordEncoder.encode("Password1")).thenReturn("hashed");
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(invocation -> {
            AppUser saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 10);
            ReflectionTestUtils.setField(saved, "createdAt", java.time.LocalDateTime.now());
            return saved;
        });

        ResponseEntity<?> response = userController.createUser(request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        UserResponse body = assertInstanceOf(UserResponse.class, response.getBody());
        assertNotNull(body);
        assertEquals("newuser@example.com", body.email());
        verify(appUserRepository).save(any(AppUser.class));
    }

    /**
     * Potwierdza odrzucenie ujemnej kwoty doładowania przed aktualizacją salda.
     *
     * @param id identyfikator użytkownika (1)
     * @param request TopUpRequest z kwotą -50
     * @return status 400, {@link AuthErrorResponse} "Amount must be greater than zero"
     */
    @Test
    @DisplayName("Doładowanie zwraca 400 dla ujemnej kwoty")
    void topUpReturnsBadRequestForNegativeAmount() {
        TopUpRequest request = new TopUpRequest(new BigDecimal("-50"));

        ResponseEntity<?> response = userController.topUp(1, request, authentication);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Amount must be greater than zero",
                assertInstanceOf(AuthErrorResponse.class, response.getBody()).getMessage());
        verify(appUserRepository, never()).findById(any());
    }

    /**
     * Sprawdza górny limit kwoty doładowania (1000 PLN).
     *
     * @param id identyfikator użytkownika (1)
     * @param request TopUpRequest z kwotą 1000.01
     * @return status 400, {@link AuthErrorResponse} z komunikatem o limicie 1000
     */
    @Test
    @DisplayName("Doładowanie zwraca 400 gdy kwota przekracza limit")
    void topUpReturnsBadRequestWhenAmountExceedsLimit() {
        TopUpRequest request = new TopUpRequest(new BigDecimal("1000.01"));

        ResponseEntity<?> response = userController.topUp(1, request, authentication);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(assertInstanceOf(AuthErrorResponse.class, response.getBody())
                .getMessage()
                .contains("1000"));
    }

    /**
     * Weryfikuje poprawne doładowanie konta zalogowanego klienta.
     *
     * @param id identyfikator użytkownika (5)
     * @param request TopUpRequest z kwotą 250
     * @param authentication token JWT klienta (właściciel konta)
     * @return status 200, saldo zwiększone z 100 do 350 PLN
     */
    @Test
    @DisplayName("Doładowanie zwieksza saldo uzytkownika")
    void topUpIncreasesBalanceForAuthorizedClient() {
        AppUser authUser = buildUser(5, "client@example.com", "CLIENT");
        AppUser targetUser = buildUser(5, "client@example.com", "CLIENT");
        targetUser.setAccountBalance(new BigDecimal("100.00"));

        when(authentication.getName()).thenReturn("client@example.com");
        when(appUserRepository.findByEmail("client@example.com")).thenReturn(Optional.of(authUser));
        when(appUserRepository.findById(5)).thenReturn(Optional.of(targetUser));
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ResponseEntity<?> response = userController.topUp(
                5,
                new TopUpRequest(new BigDecimal("250")),
                authentication
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());

        ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        verify(appUserRepository).save(captor.capture());
        assertEquals(new BigDecimal("350.00"), captor.getValue().getAccountBalance());
    }

    /**
     * Sprawdza, że klient nie może doładować cudzego konta.
     *
     * @param id identyfikator obcego konta (99)
     * @param authentication token JWT klienta (id 5)
     * @return status 403, brak zapisu salda
     */
    @Test
    @DisplayName("Doładowanie zwraca 403 gdy klient probuje doladowac cudze konto")
    void topUpReturnsForbiddenWhenClientTriesAnotherAccount() {
        AppUser authUser = buildUser(5, "client@example.com", "CLIENT");

        when(authentication.getName()).thenReturn("client@example.com");
        when(appUserRepository.findByEmail("client@example.com")).thenReturn(Optional.of(authUser));

        ResponseEntity<?> response = userController.topUp(
                99,
                new TopUpRequest(new BigDecimal("100")),
                authentication
        );

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        verify(appUserRepository, never()).save(any(AppUser.class));
    }

    private UserUpsertRequest validCreateRequest() {
        UserUpsertRequest request = new UserUpsertRequest();
        request.setEmail("newuser@example.com");
        request.setPassword("Password1");
        request.setRole("CLIENT");
        request.setFirstName("Jan");
        request.setLastName("Kowalski");
        request.setPhoneNumber("600700800");
        return request;
    }

    private AppUser buildUser(int id, String email, String role) {
        AppUser user = new AppUser();
        ReflectionTestUtils.setField(user, "id", id);
        user.setEmail(email);
        user.setRole(role);
        user.setFirstName("Jan");
        user.setLastName("Kowalski");
        user.setAccountBalance(BigDecimal.ZERO);
        return user;
    }
}
