package biali.fitmanager.backend.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import biali.fitmanager.backend.dto.AuthErrorResponse;
import biali.fitmanager.backend.dto.MembershipUpsertRequest;
import biali.fitmanager.backend.dto.PurchaseMembershipRequest;
import biali.fitmanager.backend.model.MembershipType;
import biali.fitmanager.backend.repository.MembershipTypeRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import biali.fitmanager.backend.model.Membership;
import biali.fitmanager.backend.repository.AppUserRepository;
import biali.fitmanager.backend.repository.MembershipRepository;
import biali.fitmanager.backend.model.Payment;
import biali.fitmanager.backend.repository.PaymentRepository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Zarządzanie karnetami: lista, zakup i CRUD.
 */
@RestController
@RequestMapping("/api/memberships")
@CrossOrigin(origins = "*") // Dodane dla ułatwienia komunikacji z frontendem
public class MembershipController {

    private final MembershipRepository membershipRepository;
    private final AppUserRepository appUserRepository;
    private final MembershipTypeRepository membershipTypeRepository;
    private final PaymentRepository paymentRepository;

    public MembershipController(MembershipRepository membershipRepository, AppUserRepository appUserRepository, MembershipTypeRepository membershipTypeRepository, PaymentRepository paymentRepository) {
        this.membershipRepository = membershipRepository;
        this.appUserRepository = appUserRepository;
        this.membershipTypeRepository = membershipTypeRepository;
        this.paymentRepository = paymentRepository;
    }

    /**
     * Zwraca karnety, opcjonalnie filtrowane po użytkowniku.
     *
     * @param userId opcjonalny filtr po ID użytkownika
     * @return lista {@link Membership}
     */
    @GetMapping
    public List<Membership> getMemberships(@RequestParam(required = false) Integer userId) {
        if (userId != null) {
            return membershipRepository.findAllByUserId(userId);
        }
        return membershipRepository.findAll();
    }

    /**
     * Zwraca karnet po ID.
     *
     * @param id identyfikator karnetu
     * @return 200 z {@link Membership}, 404 gdy nie znaleziono
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getMembership(@PathVariable Integer id) {
        return membershipRepository.findById(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new AuthErrorResponse("Membership not found")));
    }

    /**
     * Zwraca aktywny karnet zalogowanego użytkownika.
     *
     * @param authentication zalogowany użytkownik
     * @return 200 z {@link Membership}, 401/404 gdy brak aktywnego karnetu
     */
    @GetMapping("/me")
    public ResponseEntity<?> getMyActiveMembership(Authentication authentication) {
        var authEmail = authentication.getName();
        var authUserOpt = appUserRepository.findByEmail(authEmail);
        if (authUserOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new AuthErrorResponse("Unauthorized"));
        }

        var userId = authUserOpt.get().getId();
        return membershipRepository.findFirstByUserIdAndStatusOrderByEndDateDesc(userId, "ACTIVE")
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new AuthErrorResponse("No active membership")));
    }

    /**
     * Tworzy nowy karnet i rejestruje płatność.
     *
     * @param request dane karnetu (userId, typ, daty, status)
     * @return 201 z {@link Membership}, 400 przy błędach walidacji
     */
    @PostMapping
    public ResponseEntity<?> createMembership(@RequestBody MembershipUpsertRequest request) {
        String validationError = validateRequest(request);
        if (validationError != null) {
            return ResponseEntity.badRequest().body(new AuthErrorResponse(validationError));
        }

        Membership membership = new Membership();
        fillMembership(membership, request);
        
        Membership saved = membershipRepository.save(membership);
        // Record payment for the purchased membership and link membership_id
        Payment payment = new Payment();
        payment.setUserId(request.getUserId());
        payment.setMembershipId(saved.getId());
        BigDecimal price = request.getMembershipType().getPrice() == null
            ? BigDecimal.ZERO
            : request.getMembershipType().getPrice();
        payment.setAmount(price);
        payment.setStatus("SUCCESS");
        paymentRepository.save(payment);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    /**
     * Aktualizuje istniejący karnet.
     *
     * @param id identyfikator karnetu
     * @param request pola do aktualizacji
     * @return 200 z {@link Membership}, 400/404 przy błędach
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> updateMembership(@PathVariable Integer id, @RequestBody MembershipUpsertRequest request) {
        return membershipRepository.findById(id)
                .<ResponseEntity<?>>map(existing -> {
                    String validationError = validateRequest(request);
                    if (validationError != null) {
                        return ResponseEntity.badRequest().body(new AuthErrorResponse(validationError));
                    }

                    fillMembership(existing, request);
                    return ResponseEntity.ok(membershipRepository.save(existing));
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new AuthErrorResponse("Membership not found")));
    }

    /**
     * Usuwa karnet po ID.
     *
     * @param id identyfikator karnetu
     * @return 204 po sukcesie, 404 gdy nie znaleziono
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteMembership(@PathVariable Integer id) {
        if (!membershipRepository.existsById(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new AuthErrorResponse("Membership not found"));
        }
        membershipRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Kupuje karnet z salda użytkownika.
     *
     * @param request userId i membershipTypeId
     * @return 201 z {@link Membership}, 400 przy braku środków, 404/409 przy błędach
     */
    @PostMapping("/purchase")
    @Transactional
    public ResponseEntity<?> purchaseMembership(@RequestBody PurchaseMembershipRequest request) {
        if (request == null || request.getUserId() == null || request.getMembershipTypeId() == null) {
            return ResponseEntity.badRequest().body(new AuthErrorResponse("Invalid purchase payload"));
        }

        if (membershipRepository.existsByUserIdAndStatus(request.getUserId(), "ACTIVE")) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new AuthErrorResponse("User already has an active membership"));
        }

        var userOpt = appUserRepository.findById(request.getUserId());
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new AuthErrorResponse("User not found"));
        }

        var mtOpt = membershipTypeRepository.findById(request.getMembershipTypeId());
        if (mtOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new AuthErrorResponse("Membership type not found"));
        }

        var user = userOpt.get();
        MembershipType mt = mtOpt.get();
        BigDecimal price = mt.getPrice() == null ? BigDecimal.ZERO : mt.getPrice();

        if (user.getAccountBalance() == null || user.getAccountBalance().compareTo(price) < 0) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new AuthErrorResponse("Insufficient funds"));
        }

        // deduct
        user.setAccountBalance(user.getAccountBalance().subtract(price));
        appUserRepository.save(user);

        // create membership
        Membership membership = new Membership();
        membership.setUserId(user.getId());
        membership.setMembershipType(mt);
        LocalDate start = LocalDate.now();
        membership.setStartDate(start);
        membership.setEndDate(start.plusDays(mt.getDurationDays()));
        membership.setStatus("ACTIVE");

        Membership saved = membershipRepository.save(membership);

        Payment payment = new Payment();
        payment.setUserId(user.getId());
        payment.setMembershipId(saved.getId());
        payment.setAmount(price);
        payment.setStatus("SUCCESS");
        paymentRepository.save(payment);

        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    /**
     * Waliduje dane karnetu przed zapisem.
     *
     * @param request dane karnetu
     * @return komunikat błędu lub null gdy dane poprawne
     */
    private String validateRequest(MembershipUpsertRequest request) {
        if (request == null
                || request.getUserId() == null
                || request.getMembershipType() == null 
                || request.getStartDate() == null
                || request.getEndDate() == null
                || request.getStatus() == null || request.getStatus().isBlank()) {
            return "Invalid membership payload";
        }

        if (!appUserRepository.existsById(request.getUserId())) {
            return "User does not exist";
        }

        if (request.getEndDate().isBefore(request.getStartDate())) {
            return "End date cannot be before start date";
        }

        return null;
    }

    /**
     * Mapuje dane z żądania na encję karnetu.
     *
     * @param membership encja do uzupełnienia
     * @param request dane z żądania
     */
    private void fillMembership(Membership membership, MembershipUpsertRequest request) {
        membership.setUserId(request.getUserId());
        membership.setMembershipType(request.getMembershipType()); // Tutaj trafia nasz Enum
        membership.setStartDate(request.getStartDate());
        membership.setEndDate(request.getEndDate());
        membership.setStatus(request.getStatus().trim().toUpperCase());
    }
}