package biali.fitmanager.backend.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import biali.fitmanager.backend.dto.AuthErrorResponse;
import biali.fitmanager.backend.dto.MembershipUpsertRequest;
import biali.fitmanager.backend.model.Membership;
import biali.fitmanager.backend.repository.AppUserRepository;
import biali.fitmanager.backend.repository.MembershipRepository;

@RestController
@RequestMapping("/api/memberships")
@CrossOrigin(origins = "*") // Dodane dla ułatwienia komunikacji z frontendem
public class MembershipController {

    private final MembershipRepository membershipRepository;
    private final AppUserRepository appUserRepository;

    public MembershipController(MembershipRepository membershipRepository, AppUserRepository appUserRepository) {
        this.membershipRepository = membershipRepository;
        this.appUserRepository = appUserRepository;
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