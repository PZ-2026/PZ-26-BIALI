package biali.fitmanager.backend.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
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

import biali.fitmanager.backend.dto.AuthErrorResponse;
import biali.fitmanager.backend.dto.MembershipUpsertRequest;
import biali.fitmanager.backend.dto.PurchaseMembershipRequest;
import biali.fitmanager.backend.model.AppUser;
import biali.fitmanager.backend.model.Membership;
import biali.fitmanager.backend.model.MembershipType;
import biali.fitmanager.backend.model.Payment;
import biali.fitmanager.backend.repository.AppUserRepository;
import biali.fitmanager.backend.repository.MembershipRepository;
import biali.fitmanager.backend.repository.MembershipTypeRepository;
import biali.fitmanager.backend.repository.PaymentRepository;

/**
 * Testy jednostkowe {@link MembershipController}: tworzenie i zakup karnetów.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Testy kontrolera karnetow")
class MembershipControllerTest {

    @Mock
    private MembershipRepository membershipRepository;

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private MembershipTypeRepository membershipTypeRepository;

    @Mock
    private PaymentRepository paymentRepository;

    private MembershipController membershipController;

    @BeforeEach
    void setUp() {
        membershipController = new MembershipController(membershipRepository, appUserRepository, membershipTypeRepository, paymentRepository);
    }

    /**
     * Weryfikuje utworzenie płatności powiązanej z zapisanym karnetem.
     *
     * @param request MembershipUpsertRequest z typem karnetu 149.99 PLN
     * @return status 201, {@link Membership} ACTIVE, Payment z membershipId i kwotą 149.99
     */
    @Test
    @DisplayName("Tworzenie karnetu tworzy powiazana platnosc z membership_id")
    void createMembershipCreatesPaymentLinkedToSavedMembership() {
        MembershipType type = new MembershipType();
        type.setPrice(new BigDecimal("149.99"));

        MembershipUpsertRequest request = new MembershipUpsertRequest();
        request.setUserId(10);
        request.setMembershipType(type);
        request.setStartDate(LocalDate.now());
        request.setEndDate(LocalDate.now().plusDays(30));
        request.setStatus("active");

        when(appUserRepository.existsById(10)).thenReturn(true);

        Membership savedMembership = new Membership();
        savedMembership.setUserId(10);
        savedMembership.setMembershipType(type);
        savedMembership.setStartDate(request.getStartDate());
        savedMembership.setEndDate(request.getEndDate());
        savedMembership.setStatus("ACTIVE");

        when(membershipRepository.save(any(Membership.class))).thenAnswer(invocation -> {
            Membership arg = invocation.getArgument(0);
            java.lang.reflect.Field idField = Membership.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(arg, 77);
            return arg;
        });

        ResponseEntity<?> response = membershipController.createMembership(request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        Membership body = assertInstanceOf(Membership.class, response.getBody());
        assertEquals("ACTIVE", body.getStatus());

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(paymentCaptor.capture());

        Payment savedPayment = paymentCaptor.getValue();
        assertEquals(10, savedPayment.getUserId());
        assertEquals(77, savedPayment.getMembershipId());
        assertEquals(new BigDecimal("149.99"), savedPayment.getAmount());
        assertEquals("SUCCESS", savedPayment.getStatus());
    }

    /**
     * Sprawdza zwrot 400 przy niekompletnym payloadzie karnetu.
     *
     * @param request MembershipUpsertRequest tylko z userId (brak typu i dat)
     * @return status 400, {@link AuthErrorResponse} "Invalid membership payload", brak zapisu
     */
    @Test
    @DisplayName("Tworzenie karnetu zwraca 400 dla nieprawidlowego payloadu")
    void createMembershipReturnsBadRequestForInvalidPayload() {
        MembershipUpsertRequest request = new MembershipUpsertRequest();
        request.setUserId(10);

        ResponseEntity<?> response = membershipController.createMembership(request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        AuthErrorResponse body = assertInstanceOf(AuthErrorResponse.class, response.getBody());
        assertEquals("Invalid membership payload", body.getMessage());
        verify(membershipRepository, never()).save(any(Membership.class));
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    /**
     * Potwierdza odrzucenie zakupu karnetu przy zbyt niskim saldzie.
     *
     * @param request PurchaseMembershipRequest (userId 11, typ karnetu 149.99 PLN)
     * @return status 400, {@link AuthErrorResponse} "Insufficient funds", brak zapisu karnetu/płatności
     */
    @Test
    @DisplayName("Zakup karnetu zwraca 400 gdy brak srodkow")
    void purchaseMembershipReturnsBadRequestWhenFundsAreInsufficient() {
        PurchaseMembershipRequest request = new PurchaseMembershipRequest();
        request.setUserId(11);
        request.setMembershipTypeId(3L);

        AppUser user = new AppUser();
        user.setAccountBalance(new BigDecimal("50.00"));

        MembershipType type = new MembershipType();
        type.setPrice(new BigDecimal("149.99"));
        type.setDurationDays(30);

        when(membershipRepository.existsByUserIdAndStatus(11, "ACTIVE")).thenReturn(false);
        when(appUserRepository.findById(11)).thenReturn(Optional.of(user));
        when(membershipTypeRepository.findById(3L)).thenReturn(Optional.of(type));

        ResponseEntity<?> response = membershipController.purchaseMembership(request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        AuthErrorResponse body = assertInstanceOf(AuthErrorResponse.class, response.getBody());
        assertEquals("Insufficient funds", body.getMessage());
        verify(membershipRepository, never()).save(any(Membership.class));
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    /**
     * Weryfikuje poprawny zakup karnetu wraz z aktualizacją salda i płatności.
     *
     * @param request PurchaseMembershipRequest (userId 12, saldo 300 PLN, karnet 149.99 PLN)
     * @return status 201, saldo 150.01 PLN, Payment SUCCESS z membershipId
     */
    @Test
    @DisplayName("Zakup karnetu tworzy karnet i platnosc gdy srodki wystarczajace")
    void purchaseMembershipCreatesMembershipAndPaymentWhenFundsAreEnough() {
        PurchaseMembershipRequest request = new PurchaseMembershipRequest();
        request.setUserId(12);
        request.setMembershipTypeId(5L);

        AppUser user = new AppUser();
        user.setAccountBalance(new BigDecimal("300.00"));

        MembershipType type = new MembershipType();
        type.setPrice(new BigDecimal("149.99"));
        type.setDurationDays(30);

        when(membershipRepository.existsByUserIdAndStatus(12, "ACTIVE")).thenReturn(false);
        when(appUserRepository.findById(12)).thenReturn(Optional.of(user));
        when(membershipTypeRepository.findById(5L)).thenReturn(Optional.of(type));
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(membershipRepository.save(any(Membership.class))).thenAnswer(invocation -> {
            Membership arg = invocation.getArgument(0);
            java.lang.reflect.Field idField = Membership.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(arg, 88);
            return arg;
        });

        ResponseEntity<?> response = membershipController.purchaseMembership(request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());

        ArgumentCaptor<AppUser> userCaptor = ArgumentCaptor.forClass(AppUser.class);
        verify(appUserRepository).save(userCaptor.capture());
        assertEquals(new BigDecimal("150.01"), userCaptor.getValue().getAccountBalance());

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(paymentCaptor.capture());

        Payment savedPayment = paymentCaptor.getValue();
        assertEquals(88, savedPayment.getMembershipId());
        assertEquals(new BigDecimal("149.99"), savedPayment.getAmount());
        assertEquals("SUCCESS", savedPayment.getStatus());
    }
}
