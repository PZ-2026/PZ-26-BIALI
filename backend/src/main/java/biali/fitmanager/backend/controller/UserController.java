package biali.fitmanager.backend.controller;

import java.util.List;
import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import biali.fitmanager.backend.dto.AuthErrorResponse;
import biali.fitmanager.backend.dto.UserResponse;
import biali.fitmanager.backend.dto.UserUpsertRequest;
import biali.fitmanager.backend.dto.TopUpRequest;
import biali.fitmanager.backend.model.AppUser;
import biali.fitmanager.backend.model.Membership;
import biali.fitmanager.backend.model.MembershipType;
import biali.fitmanager.backend.model.Payment;
import biali.fitmanager.backend.repository.AppUserRepository;
import biali.fitmanager.backend.repository.MembershipRepository;
import biali.fitmanager.backend.repository.MembershipTypeRepository;
import biali.fitmanager.backend.repository.PaymentRepository;
import biali.fitmanager.backend.validation.InputValidator;

/**
 * CRUD użytkowników, doładowanie salda i zakup karnetu.
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final MembershipTypeRepository membershipTypeRepository;
    private final MembershipRepository membershipRepository;
    private final PaymentRepository paymentRepository;

    public UserController(AppUserRepository appUserRepository,
                          PasswordEncoder passwordEncoder,
                          MembershipTypeRepository membershipTypeRepository,
                          MembershipRepository membershipRepository,
                          PaymentRepository paymentRepository) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.membershipTypeRepository = membershipTypeRepository;
        this.membershipRepository = membershipRepository;
        this.paymentRepository = paymentRepository;
    }

    /**
     * Zwraca wszystkich użytkowników.
     *
     * @return lista {@link UserResponse}
     */
    @GetMapping
    public List<UserResponse> getUsers() {
        return appUserRepository.findAll().stream().map(this::toResponse).toList();
    }

    /**
     * Zwraca użytkownika po ID.
     *
     * @param id identyfikator użytkownika
     * @return 200 z {@link UserResponse}, 404 gdy nie znaleziono
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getUserById(@PathVariable Integer id) {
        return appUserRepository.findById(id)
                .<ResponseEntity<?>>map(user -> ResponseEntity.ok(toResponse(user)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new AuthErrorResponse("User not found")));
    }

    /**
     * Tworzy nowego użytkownika.
     *
     * @param request dane użytkownika
     * @return 201 z {@link UserResponse}, 400/409 przy błędach
     */
    @PostMapping
    public ResponseEntity<?> createUser(@RequestBody UserUpsertRequest request) {
        String validationError = InputValidator.validateUserCreate(request);
        if (validationError != null) {
            return ResponseEntity.badRequest().body(new AuthErrorResponse(validationError));
        }

        if (appUserRepository.existsByEmail(request.getEmail())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new AuthErrorResponse("Email already exists"));
        }

        AppUser user = new AppUser();
        applyUpsert(user, request, true);
        AppUser saved = appUserRepository.save(user);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(saved));
    }

    /**
     * Doładowuje saldo konta użytkownika.
     *
     * @param id identyfikator użytkownika
     * @param request kwota doładowania (amount)
     * @param authentication zalogowany użytkownik (właściciel konta lub admin)
     * @return 200 z {@link UserResponse}, 400/401/403/404 przy błędach
     */
    @PostMapping("/{id}/topup")
    public ResponseEntity<?> topUp(@PathVariable Integer id, @RequestBody TopUpRequest request, org.springframework.security.core.Authentication authentication) {
        String amountError = InputValidator.validateTopUpAmount(request == null ? null : request.getAmount());
        if (amountError != null) {
            return ResponseEntity.badRequest().body(new AuthErrorResponse(amountError));
        }
        var authEmail = authentication.getName();
        var authUserOpt = appUserRepository.findByEmail(authEmail);
        if (authUserOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new AuthErrorResponse("Unauthorized"));
        }

        var authUser = authUserOpt.get();
        if (!"ADMIN".equalsIgnoreCase(authUser.getRole()) && !authUser.getId().equals(id)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new AuthErrorResponse("Forbidden"));
        }

        return appUserRepository.findById(id)
                .<ResponseEntity<?>>map(user -> {
                    BigDecimal newBalance = user.getAccountBalance().add(request.getAmount());
                    user.setAccountBalance(newBalance);
                    appUserRepository.save(user);
                    return ResponseEntity.ok(toResponse(user));
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new AuthErrorResponse("User not found")));
    }

    /**
     * Kupuje karnet z salda użytkownika.
     *
     * @param id identyfikator użytkownika
     * @param membershipTypeId identyfikator typu karnetu
     * @param authentication zalogowany użytkownik (właściciel konta lub admin)
     * @return 200 z {@link Membership}, 400 przy braku środków, 401/403/404/409 przy błędach
     */
    @PostMapping("/{id}/purchase-membership/{membershipTypeId}")
    @Transactional
    public ResponseEntity<?> purchaseMembership(@PathVariable Integer id, @PathVariable Integer membershipTypeId, org.springframework.security.core.Authentication authentication) {
        var authEmail = authentication.getName();
        var authUserOpt = appUserRepository.findByEmail(authEmail);
        if (authUserOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new AuthErrorResponse("Unauthorized"));
        }

        var authUser = authUserOpt.get();
        if (!"ADMIN".equalsIgnoreCase(authUser.getRole()) && !authUser.getId().equals(id)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new AuthErrorResponse("Forbidden"));
        }

        if (membershipRepository.existsByUserIdAndStatus(id, "ACTIVE")) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new AuthErrorResponse("User already has an active membership"));
        }

        var userOpt = appUserRepository.findById(id);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new AuthErrorResponse("User not found"));
        }
        var mtOpt = membershipTypeRepository.findById(membershipTypeId.longValue());
        if (mtOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new AuthErrorResponse("Membership type not found"));
        }

        AppUser user = userOpt.get();
        MembershipType mt = mtOpt.get();
        BigDecimal price = mt.getPrice();
        if (user.getAccountBalance().compareTo(price) < 0) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new AuthErrorResponse("Insufficient funds"));
        }

        // Deduct and save user
        user.setAccountBalance(user.getAccountBalance().subtract(price));
        appUserRepository.save(user);

        // Create membership
        Membership membership = new Membership();
        membership.setUserId(user.getId());
        membership.setMembershipType(mt);
        var start = LocalDate.now();
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

        return ResponseEntity.ok(saved);
    }

    /**
     * Aktualizuje dane użytkownika.
     *
     * @param id identyfikator użytkownika
     * @param request pola do aktualizacji
     * @return 200 z {@link UserResponse}, 400/404/409 przy błędach
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> updateUser(@PathVariable Integer id, @RequestBody UserUpsertRequest request) {
        String validationError = InputValidator.validateUserUpdate(request);
        if (validationError != null) {
            return ResponseEntity.badRequest().body(new AuthErrorResponse(validationError));
        }

        return appUserRepository.findById(id)
                .<ResponseEntity<?>>map(existing -> {
                    if (request.getEmail() != null
                            && !request.getEmail().equalsIgnoreCase(existing.getEmail())
                            && appUserRepository.existsByEmail(request.getEmail())) {
                        return ResponseEntity.status(HttpStatus.CONFLICT)
                                .body(new AuthErrorResponse("Email already exists"));
                    }

                    applyUpsert(existing, request, false);
                    AppUser saved = appUserRepository.save(existing);
                    return ResponseEntity.ok(toResponse(saved));
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new AuthErrorResponse("User not found")));
    }

    /**
     * Usuwa użytkownika po ID.
     *
     * @param id identyfikator użytkownika
     * @return 204 po sukcesie, 404 gdy nie znaleziono
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable Integer id) {
        if (!appUserRepository.existsById(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new AuthErrorResponse("User not found"));
        }
        appUserRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Kopiuje pola z żądania do encji użytkownika.
     *
     * @param user encja do uzupełnienia
     * @param request dane z żądania
     * @param createMode true przy tworzeniu nowego użytkownika
     */
    private void applyUpsert(AppUser user, UserUpsertRequest request, boolean createMode) {
        if (request.getEmail() != null) {
            user.setEmail(request.getEmail().trim().toLowerCase());
        }
        if (request.getRole() != null) {
            user.setRole(request.getRole().trim().toUpperCase());
        }
        if (request.getFirstName() != null) {
            user.setFirstName(request.getFirstName().trim());
        }
        if (request.getLastName() != null) {
            user.setLastName(request.getLastName().trim());
        }
        if (request.getPhoneNumber() != null) {
            user.setPhoneNumber(request.getPhoneNumber().trim());
        }
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        }

        if (!createMode) {
            if (user.getRole() == null) {
                user.setRole("CLIENT");
            }
            return;
        }

        if (user.getRole() == null || user.getRole().isBlank()) {
            user.setRole("CLIENT");
        }
    }

    /**
     * Mapuje encję użytkownika na DTO odpowiedzi.
     *
     * @param user encja AppUser
     * @return {@link UserResponse}
     */
    private UserResponse toResponse(AppUser user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getRole(),
                user.getFirstName(),
                user.getLastName(),
                user.getPhoneNumber(),
                user.getCreatedAt(),
                user.getAccountBalance()
        );
    }
}
