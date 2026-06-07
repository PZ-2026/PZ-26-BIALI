package biali.fitmanager.backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    private final PasswordEncoder passwordEncoder;

    public ProfileController(AppUserRepository appUserRepository,
                             biali.fitmanager.backend.repository.TrainerRentalRepository trainerRentalRepository,
                             PasswordEncoder passwordEncoder) {
        this.appUserRepository = appUserRepository;
        this.trainerRentalRepository = trainerRentalRepository;
        this.passwordEncoder = passwordEncoder;
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

    @PostMapping("/api/me/password")
    public ResponseEntity<?> changePassword(Authentication authentication, @RequestBody ChangePasswordRequest request) {
        if (request == null || isBlank(request.currentPassword()) || isBlank(request.newPassword())) {
            return ResponseEntity.badRequest().body(new AuthErrorResponse("Invalid password payload"));
        }

        AppUser user = appUserRepository.findByEmail(authentication.getName()).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new AuthErrorResponse("User not found"));
        }

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new AuthErrorResponse("Current password is incorrect"));
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        appUserRepository.save(user);
        return ResponseEntity.ok().build();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record ChangePasswordRequest(String currentPassword, String newPassword) {}
}
