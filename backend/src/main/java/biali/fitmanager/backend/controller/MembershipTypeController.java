package biali.fitmanager.backend.controller;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import biali.fitmanager.backend.model.MembershipType;
import biali.fitmanager.backend.repository.MembershipTypeRepository;
import biali.fitmanager.backend.dto.AuthErrorResponse;
import biali.fitmanager.backend.validation.InputValidator;

/**
 * Typy karnetów: lista publiczna i zarządzanie przez admina.
 */
@RestController
@RequestMapping("/api")
public class MembershipTypeController {

     private final MembershipTypeRepository repository;

    public MembershipTypeController(MembershipTypeRepository repository) {
        this.repository = repository;
    }

    /**
     * Zwraca wszystkie dostępne typy karnetów.
     *
     * @return lista {@link MembershipType} (code, name, price, durationDays, description)
     */
    @GetMapping("/membership-types")
    public List<MembershipType> getAll() {
        return repository.findAll();
    }

    /**
     * Tworzy nowy typ karnetu (admin).
     *
     * @param membershipType dane typu karnetu
     * @return 201 z {@link MembershipType}, 400 przy błędach walidacji
     */
    @PostMapping("/admin/membership-types")
    public ResponseEntity<?> create(@RequestBody MembershipType membershipType) {
        String validationError = InputValidator.validateMembershipType(
                membershipType == null ? null : membershipType.getName(),
                membershipType == null ? null : membershipType.getPrice(),
                membershipType == null ? null : membershipType.getDurationDays()
        );
        if (validationError != null) {
            return ResponseEntity.badRequest().body(new AuthErrorResponse(validationError));
        }

        try {
            MembershipType saved = repository.save(membershipType);
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new AuthErrorResponse("Unable to create membership type"));
        }
    }

    /**
     * Aktualizuje typ karnetu (admin).
     *
     * @param id identyfikator typu karnetu
     * @param membershipType pola do aktualizacji
     * @return 200 z {@link MembershipType}, 404 gdy nie znaleziono
     */
    @PutMapping("/admin/membership-types/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody MembershipType membershipType) {
        String validationError = InputValidator.validateMembershipType(
                membershipType == null ? null : membershipType.getName(),
                membershipType == null ? null : membershipType.getPrice(),
                membershipType == null ? null : membershipType.getDurationDays()
        );
        if (validationError != null) {
            return ResponseEntity.badRequest().body(new AuthErrorResponse(validationError));
        }

        return repository.findById(id)
            .map(existing -> {
                existing.setName(membershipType.getName());
                existing.setPrice(membershipType.getPrice());
                existing.setDurationDays(membershipType.getDurationDays());
                existing.setDescription(membershipType.getDescription());
                if (membershipType.getCode() != null) {
                    existing.setCode(membershipType.getCode());
                }
                MembershipType updated = repository.save(existing);
                return ResponseEntity.ok(updated);
            })
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Usuwa typ karnetu (admin).
     *
     * @param id identyfikator typu karnetu
     * @return 204 po sukcesie, 404 gdy nie znaleziono
     */
    @DeleteMapping("/admin/membership-types/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!repository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
