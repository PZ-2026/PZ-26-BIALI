package biali.fitmanager.backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import biali.fitmanager.backend.dto.AuthErrorResponse;
import biali.fitmanager.backend.dto.MeResponse;
import biali.fitmanager.backend.model.AppUser;
import biali.fitmanager.backend.repository.AppUserRepository;

@RestController
public class ProfileController {

    private final AppUserRepository appUserRepository;
    private final biali.fitmanager.backend.repository.TrainerRentalRepository trainerRentalRepository;

    public ProfileController(AppUserRepository appUserRepository,
                             biali.fitmanager.backend.repository.TrainerRentalRepository trainerRentalRepository) {
        this.appUserRepository = appUserRepository;
        this.trainerRentalRepository = trainerRentalRepository;
    }

    @GetMapping("/api/me")
    public ResponseEntity<?> getCurrentUser(Authentication authentication) {
        String email = authentication.getName();
        AppUser user = appUserRepository.findByEmail(email).orElse(null);

        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new AuthErrorResponse("User not found"));
        }

        String name = (safe(user.getFirstName()) + " " + safe(user.getLastName())).trim();

        // find active trainer rental for this user (client)
        var active = trainerRentalRepository.findAllByClientId(user.getId())
            .stream()
            .filter(r -> "ACTIVE".equalsIgnoreCase(r.getStatus()))
            .findFirst();

        Integer trainerId = active.map(biali.fitmanager.backend.model.TrainerRental::getTrainerId).orElse(null);
        String trainerEnd = active.map(r -> r.getEndDate().toString()).orElse(null);

        return ResponseEntity.ok(new MeResponse(
            user.getId(),
            user.getEmail(),
            user.getRole(),
            user.getFirstName(),
            user.getLastName(),
            user.getPhoneNumber(),
            user.getAccountBalance(),
            name,
            trainerId,
            trainerEnd
        ));
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
