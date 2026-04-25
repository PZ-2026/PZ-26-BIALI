package biali.fitmanager.backend.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import biali.fitmanager.backend.dto.AuthErrorResponse;
import biali.fitmanager.backend.dto.UserResponse;
import biali.fitmanager.backend.dto.UserUpsertRequest;
import biali.fitmanager.backend.model.AppUser;
import biali.fitmanager.backend.model.TrainerClient;
import biali.fitmanager.backend.repository.AppUserRepository;
import biali.fitmanager.backend.repository.TrainerClientRepository;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private static final String ROLE_TRAINER = "TRAINER";
    private static final String ROLE_CLIENT = "CLIENT";

    private final AppUserRepository appUserRepository;
    private final TrainerClientRepository trainerClientRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminController(AppUserRepository appUserRepository,
                           TrainerClientRepository trainerClientRepository,
                           PasswordEncoder passwordEncoder) {
        this.appUserRepository = appUserRepository;
        this.trainerClientRepository = trainerClientRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping("/users")
    public List<UserResponse> getUsers(@RequestParam(required = false) String role) {
        if (role != null && !role.isBlank()) {
            return appUserRepository.findAllByRole(role.trim().toUpperCase()).stream().map(this::toResponse).toList();
        }
        return appUserRepository.findAll().stream().map(this::toResponse).toList();
    }

    @PostMapping("/users")
    public ResponseEntity<?> createUser(@RequestBody UserUpsertRequest request) {
        if (!isValidForCreate(request)) {
            return ResponseEntity.badRequest().body(new AuthErrorResponse("Invalid user payload"));
        }
        if (appUserRepository.existsByEmail(request.getEmail().trim().toLowerCase())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new AuthErrorResponse("Email already exists"));
        }

        AppUser user = new AppUser();
        applyCommonFields(user, request, true);
        AppUser saved = appUserRepository.save(user);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(saved));
    }

    @PutMapping("/users/{id}")
    public ResponseEntity<?> updateUser(@PathVariable Integer id, @RequestBody UserUpsertRequest request) {
        return appUserRepository.findById(id)
                .<ResponseEntity<?>>map(existing -> {
                    if (request.getEmail() != null
                            && !request.getEmail().equalsIgnoreCase(existing.getEmail())
                            && appUserRepository.existsByEmail(request.getEmail().trim().toLowerCase())) {
                        return ResponseEntity.status(HttpStatus.CONFLICT)
                                .body(new AuthErrorResponse("Email already exists"));
                    }
                    applyCommonFields(existing, request, false);
                    return ResponseEntity.ok(toResponse(appUserRepository.save(existing)));
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new AuthErrorResponse("User not found")));
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable Integer id) {
        if (!appUserRepository.existsById(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new AuthErrorResponse("User not found"));
        }
        appUserRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/trainers")
    public List<UserResponse> getTrainers() {
        return appUserRepository.findAllByRole(ROLE_TRAINER).stream().map(this::toResponse).toList();
    }

    @PostMapping("/trainers")
    public ResponseEntity<?> createTrainer(@RequestBody UserUpsertRequest request) {
        request.setRole(ROLE_TRAINER);
        return createUser(request);
    }

    @PutMapping("/trainers/{id}")
    public ResponseEntity<?> updateTrainer(@PathVariable Integer id, @RequestBody UserUpsertRequest request) {
        return appUserRepository.findByIdAndRole(id, ROLE_TRAINER)
                .<ResponseEntity<?>>map(trainer -> {
                    request.setRole(ROLE_TRAINER);
                    if (request.getEmail() != null
                            && !request.getEmail().equalsIgnoreCase(trainer.getEmail())
                            && appUserRepository.existsByEmail(request.getEmail().trim().toLowerCase())) {
                        return ResponseEntity.status(HttpStatus.CONFLICT)
                                .body(new AuthErrorResponse("Email already exists"));
                    }

                    applyCommonFields(trainer, request, false);
                    trainer.setRole(ROLE_TRAINER);
                    return ResponseEntity.ok(toResponse(appUserRepository.save(trainer)));
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new AuthErrorResponse("Trainer not found")));
    }

    @DeleteMapping("/trainers/{id}")
    public ResponseEntity<?> deleteTrainer(@PathVariable Integer id) {
        if (!appUserRepository.existsByIdAndRole(id, ROLE_TRAINER)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new AuthErrorResponse("Trainer not found"));
        }
        appUserRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/trainers/{trainerId}/clients")
    public ResponseEntity<?> getTrainerClients(@PathVariable Integer trainerId) {
        if (!appUserRepository.existsByIdAndRole(trainerId, ROLE_TRAINER)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new AuthErrorResponse("Trainer not found"));
        }

        List<Integer> clientIds = trainerClientRepository.findAllByTrainerId(trainerId)
                .stream()
                .map(TrainerClient::getClientId)
                .toList();

        List<UserResponse> clients = appUserRepository.findAllById(clientIds)
                .stream()
                .filter(user -> ROLE_CLIENT.equalsIgnoreCase(user.getRole()))
                .map(this::toResponse)
                .toList();

        return ResponseEntity.ok(clients);
    }

    @PostMapping("/trainers/{trainerId}/clients/{clientId}")
    public ResponseEntity<?> assignClientToTrainer(@PathVariable Integer trainerId, @PathVariable Integer clientId) {
        if (!appUserRepository.existsByIdAndRole(trainerId, ROLE_TRAINER)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new AuthErrorResponse("Trainer not found"));
        }
        if (!appUserRepository.existsByIdAndRole(clientId, ROLE_CLIENT)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new AuthErrorResponse("Client not found"));
        }
        if (trainerClientRepository.existsByTrainerIdAndClientId(trainerId, clientId)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new AuthErrorResponse("Client already assigned to trainer"));
        }

        TrainerClient relation = new TrainerClient();
        relation.setTrainerId(trainerId);
        relation.setClientId(clientId);
        trainerClientRepository.save(relation);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/trainers/{trainerId}/clients/{clientId}")
    public ResponseEntity<?> unassignClientFromTrainer(@PathVariable Integer trainerId, @PathVariable Integer clientId) {
        if (!trainerClientRepository.existsByTrainerIdAndClientId(trainerId, clientId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new AuthErrorResponse("Trainer-client relation not found"));
        }

        trainerClientRepository.deleteByTrainerIdAndClientId(trainerId, clientId);
        return ResponseEntity.noContent().build();
    }

    private void applyCommonFields(AppUser user, UserUpsertRequest request, boolean createMode) {
        if (request.getEmail() != null) {
            user.setEmail(request.getEmail().trim().toLowerCase());
        }
        if (request.getRole() != null && !request.getRole().isBlank()) {
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

        if (createMode && (user.getRole() == null || user.getRole().isBlank())) {
            user.setRole(ROLE_CLIENT);
        }
    }

    private boolean isValidForCreate(UserUpsertRequest request) {
        return request != null
                && request.getEmail() != null && !request.getEmail().isBlank()
                && request.getPassword() != null && !request.getPassword().isBlank()
                && request.getFirstName() != null && !request.getFirstName().isBlank()
                && request.getLastName() != null && !request.getLastName().isBlank();
    }

    private UserResponse toResponse(AppUser user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getRole(),
                user.getFirstName(),
                user.getLastName(),
                user.getPhoneNumber(),
                user.getCreatedAt()
        );
    }
}
