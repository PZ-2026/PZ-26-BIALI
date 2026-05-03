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
import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping("/api/memberships")
@CrossOrigin(origins = "*") // Dodane dla ułatwienia komunikacji z frontendem
public class MembershipController {

    private final MembershipRepository membershipRepository;
    private final AppUserRepository appUserRepository;
    private final MembershipTypeRepository membershipTypeRepository;

    public MembershipController(MembershipRepository membershipRepository, AppUserRepository appUserRepository, MembershipTypeRepository membershipTypeRepository) {
        this.membershipRepository = membershipRepository;
        this.appUserRepository = appUserRepository;
        this.membershipTypeRepository = membershipTypeRepository;
    }

    // 1. Pobieranie wszystkich karnetów (opcjonalnie filtrowanie po userId)
    @GetMapping
    public List<Membership> getMemberships(@RequestParam(required = false) Integer userId) {
        if (userId != null) {
            return membershipRepository.findAllByUserId(userId);
        }
        return membershipRepository.findAll();
    }

    // 2. Pobieranie pojedynczego karnetu po ID
    @GetMapping("/{id}")
    public ResponseEntity<?> getMembership(@PathVariable Integer id) {
        return membershipRepository.findById(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new AuthErrorResponse("Membership not found")));
    }

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

    // 3. Tworzenie nowego karnetu
    @PostMapping
    public ResponseEntity<?> createMembership(@RequestBody MembershipUpsertRequest request) {
        String validationError = validateRequest(request);
        if (validationError != null) {
            return ResponseEntity.badRequest().body(new AuthErrorResponse(validationError));
        }

        Membership membership = new Membership();
        fillMembership(membership, request);
        
        Membership saved = membershipRepository.save(membership);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    // 4. Aktualizacja istniejącego karnetu
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

    // 5. Usuwanie karnetu
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteMembership(@PathVariable Integer id) {
        if (!membershipRepository.existsById(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new AuthErrorResponse("Membership not found"));
        }
        membershipRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // 6. Kupno karnetu (płatność z salda użytkownika)
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
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    // Metoda pomocnicza: Walidacja danych wejściowych
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

    // Metoda pomocnicza: Mapowanie DTO -> Entity
    private void fillMembership(Membership membership, MembershipUpsertRequest request) {
        membership.setUserId(request.getUserId());
        membership.setMembershipType(request.getMembershipType()); // Tutaj trafia nasz Enum
        membership.setStartDate(request.getStartDate());
        membership.setEndDate(request.getEndDate());
        membership.setStatus(request.getStatus().trim().toUpperCase());
    }
}