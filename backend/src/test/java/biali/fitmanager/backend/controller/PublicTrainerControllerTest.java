package biali.fitmanager.backend.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.any;
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

import biali.fitmanager.backend.dto.AuthErrorResponse;
import biali.fitmanager.backend.model.AppUser;
import biali.fitmanager.backend.model.Payment;
import biali.fitmanager.backend.model.TrainerClient;
import biali.fitmanager.backend.model.TrainerRental;
import biali.fitmanager.backend.repository.AppUserRepository;
import biali.fitmanager.backend.repository.PaymentRepository;
import biali.fitmanager.backend.repository.TrainerClientRepository;
import biali.fitmanager.backend.repository.TrainerRentalRepository;

/**
 * Testy jednostkowe {@link PublicTrainerController}: wybór trenera przez klienta.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Testy kontrolera publicznego trenerow")
class PublicTrainerControllerTest {

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private TrainerClientRepository trainerClientRepository;

    @Mock
    private TrainerRentalRepository trainerRentalRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private Authentication authentication;

    private PublicTrainerController publicTrainerController;

    @BeforeEach
    void setUp() {
        publicTrainerController = new PublicTrainerController(
                appUserRepository,
                trainerClientRepository,
                trainerRentalRepository,
                paymentRepository
        );
    }

    /**
     * Weryfikuje odmowę wyboru trenera dla użytkownika bez roli CLIENT.
     *
     * @param trainerId identyfikator trenera (2)
     * @param authentication token JWT użytkownika z rolą TRAINER
     * @return status 403, {@link AuthErrorResponse} "Only clients can choose a trainer"
     */
    @Test
    @DisplayName("Wybor trenera zwraca 403 gdy uzytkownik nie jest klientem")
    void chooseTrainerReturnsForbiddenWhenUserIsNotClient() {
        AppUser user = new AppUser();
        user.setRole("TRAINER");

        when(authentication.getName()).thenReturn("trainer@example.com");
        when(appUserRepository.findByEmail("trainer@example.com")).thenReturn(Optional.of(user));

        ResponseEntity<?> response = publicTrainerController.chooseTrainer(2, authentication);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        AuthErrorResponse body = assertInstanceOf(AuthErrorResponse.class, response.getBody());
        assertEquals("Only clients can choose a trainer", body.getMessage());
    }

    /**
     * Sprawdza odrzucenie wyboru trenera przy niewystarczającym saldzie.
     *
     * @param trainerId identyfikator trenera (9)
     * @param authentication token JWT klienta z saldem 100 PLN
     * @return status 400, {@link AuthErrorResponse} "Insufficient funds", brak zapisu rental/payment
     */
    @Test
    @DisplayName("Wybor trenera zwraca 400 gdy saldo zbyt niskie")
    void chooseTrainerReturnsBadRequestWhenBalanceIsTooLow() {
        AppUser client = new AppUser();
        client.setRole("CLIENT");
        client.setAccountBalance(new BigDecimal("100.00"));

        when(authentication.getName()).thenReturn("client@example.com");
        when(appUserRepository.findByEmail("client@example.com")).thenReturn(Optional.of(client));
        when(appUserRepository.existsByIdAndRole(9, "TRAINER")).thenReturn(true);
        when(trainerRentalRepository.existsByClientIdAndStatus(null, "ACTIVE")).thenReturn(false);

        ResponseEntity<?> response = publicTrainerController.chooseTrainer(9, authentication);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        AuthErrorResponse body = assertInstanceOf(AuthErrorResponse.class, response.getBody());
        assertEquals("Insufficient funds", body.getMessage());
        verify(trainerRentalRepository, never()).save(any(TrainerRental.class));
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    /**
     * Potwierdza utworzenie wynajmu trenera, płatności i relacji trener-klient.
     *
     * @param trainerId identyfikator trenera (5)
     * @param authentication token JWT klienta z saldem 500 PLN
     * @return status 201, zapis TrainerRental (ACTIVE), Payment (199 PLN) i TrainerClient
     */
    @Test
    @DisplayName("Wybor trenera tworzy rental, platnosc i relacje")
    void chooseTrainerCreatesRentalPaymentAndRelation() {
        AppUser client = new AppUser();
        client.setRole("CLIENT");
        client.setAccountBalance(new BigDecimal("500.00"));

        try {
            java.lang.reflect.Field idField = AppUser.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(client, 15);
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }

        when(authentication.getName()).thenReturn("client@example.com");
        when(appUserRepository.findByEmail("client@example.com")).thenReturn(Optional.of(client));
        when(appUserRepository.existsByIdAndRole(5, "TRAINER")).thenReturn(true);
        when(trainerRentalRepository.existsByClientIdAndStatus(15, "ACTIVE")).thenReturn(false);
        when(trainerClientRepository.existsByTrainerIdAndClientId(5, 15)).thenReturn(false);

        ResponseEntity<?> response = publicTrainerController.chooseTrainer(5, authentication);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());

        ArgumentCaptor<AppUser> userCaptor = ArgumentCaptor.forClass(AppUser.class);
        verify(appUserRepository).save(userCaptor.capture());
        assertEquals(new BigDecimal("301.00"), userCaptor.getValue().getAccountBalance());

        ArgumentCaptor<TrainerRental> rentalCaptor = ArgumentCaptor.forClass(TrainerRental.class);
        verify(trainerRentalRepository).save(rentalCaptor.capture());
        TrainerRental rental = rentalCaptor.getValue();
        assertEquals(5, rental.getTrainerId());
        assertEquals(15, rental.getClientId());
        assertEquals("ACTIVE", rental.getStatus());

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(paymentCaptor.capture());
        Payment payment = paymentCaptor.getValue();
        assertEquals(15, payment.getUserId());
        assertEquals(null, payment.getMembershipId());
        assertEquals(new BigDecimal("199.00"), payment.getAmount());
        assertEquals("SUCCESS", payment.getStatus());

        verify(trainerClientRepository).save(any(TrainerClient.class));
    }
}
