package biali.fitmanager.backend.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import biali.fitmanager.backend.dto.AuthErrorResponse;
import biali.fitmanager.backend.dto.UserResponse;
import biali.fitmanager.backend.model.AppUser;
import biali.fitmanager.backend.model.TrainerClient;
import biali.fitmanager.backend.repository.AppUserRepository;
import biali.fitmanager.backend.repository.TrainerClientRepository;

@RestController
@RequestMapping("/api/trainer")
public class TrainerController {

    private final AppUserRepository appUserRepository;
    private final TrainerClientRepository trainerClientRepository;

    public TrainerController(AppUserRepository appUserRepository,
                             TrainerClientRepository trainerClientRepository) {
        this.appUserRepository = appUserRepository;
        this.trainerClientRepository = trainerClientRepository;
    }

    @GetMapping("/me/clients")
    public ResponseEntity<?> getMyClients(Authentication authentication) {
        String email = authentication.getName();

        AppUser trainer = appUserRepository.findByEmail(email).orElse(null);
        if (trainer == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new AuthErrorResponse("Trainer not found"));
        }
        if (!"TRAINER".equalsIgnoreCase(trainer.getRole())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new AuthErrorResponse("Only trainer can access this endpoint"));
        }

        List<Integer> clientIds = trainerClientRepository.findAllByTrainerId(trainer.getId())
                .stream()
                .map(TrainerClient::getClientId)
                .toList();

        List<UserResponse> clients = appUserRepository.findAllById(clientIds)
                .stream()
                .filter(user -> "CLIENT".equalsIgnoreCase(user.getRole()))
                .map(this::toResponse)
                .toList();

        return ResponseEntity.ok(clients);
    }

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
