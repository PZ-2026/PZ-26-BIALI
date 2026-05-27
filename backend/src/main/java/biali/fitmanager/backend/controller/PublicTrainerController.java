package biali.fitmanager.backend.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import biali.fitmanager.backend.dto.AuthErrorResponse;
import biali.fitmanager.backend.dto.UserResponse;
import biali.fitmanager.backend.model.AppUser;
import biali.fitmanager.backend.model.TrainerClient;
import biali.fitmanager.backend.model.TrainerRental;
import biali.fitmanager.backend.model.Payment;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import biali.fitmanager.backend.repository.TrainerClientRepository;
import biali.fitmanager.backend.repository.TrainerRentalRepository;
import biali.fitmanager.backend.repository.PaymentRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import biali.fitmanager.backend.repository.AppUserRepository;

@RestController
@RequestMapping("/api/trainers")
public class PublicTrainerController {

    private static final String ROLE_TRAINER = "TRAINER";

    private final AppUserRepository appUserRepository;
    private final TrainerClientRepository trainerClientRepository;
    private final TrainerRentalRepository trainerRentalRepository;
    private final PaymentRepository paymentRepository;

    public PublicTrainerController(AppUserRepository appUserRepository,
                                   TrainerClientRepository trainerClientRepository,
                                   TrainerRentalRepository trainerRentalRepository,
                                   PaymentRepository paymentRepository) {
        this.appUserRepository = appUserRepository;
        this.trainerClientRepository = trainerClientRepository;
        this.trainerRentalRepository = trainerRentalRepository;
        this.paymentRepository = paymentRepository;
    }

    @GetMapping
    public List<UserResponse> getAllTrainers() {
        return appUserRepository.findAllByRole(ROLE_TRAINER).stream().map(this::toResponse).toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getTrainerById(@PathVariable Integer id) {
        return appUserRepository.findByIdAndRole(id, ROLE_TRAINER)
                .<ResponseEntity<?>>map(trainer -> ResponseEntity.ok(toResponse(trainer)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new AuthErrorResponse("Trainer not found")));
    }

    @PostMapping("/{id}/choose")
    @Transactional
    public ResponseEntity<?> chooseTrainer(@PathVariable("id") Integer trainerId, Authentication authentication) {
        String email = authentication.getName();
        AppUser client = appUserRepository.findByEmail(email).orElse(null);
        if (client == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new AuthErrorResponse("Client not found"));
        }
        if (!"CLIENT".equalsIgnoreCase(client.getRole())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new AuthErrorResponse("Only clients can choose a trainer"));
        }

        if (!appUserRepository.existsByIdAndRole(trainerId, ROLE_TRAINER)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new AuthErrorResponse("Trainer not found"));
        }

        // If client already has an active trainer rental, they cannot choose another
        if (trainerRentalRepository.existsByClientIdAndStatus(client.getId(), "ACTIVE")) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new AuthErrorResponse("Już posiadasz trenera"));
        }

        // Price for renting a trainer for 30 days — mirror membership flow
        BigDecimal price = new BigDecimal("199.00");
        if (client.getAccountBalance().compareTo(price) < 0) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new AuthErrorResponse("Insufficient funds"));
        }

        // Deduct funds
        client.setAccountBalance(client.getAccountBalance().subtract(price));
        appUserRepository.save(client);

        // Create rental
        TrainerRental rental = new TrainerRental();
        rental.setTrainerId(trainerId);
        rental.setClientId(client.getId());
        var start = LocalDate.now();
        rental.setStartDate(start);
        rental.setEndDate(start.plusDays(30));
        rental.setStatus("ACTIVE");
        trainerRentalRepository.save(rental);

        // Record payment (membership_id left null)
        Payment payment = new Payment();
        payment.setUserId(client.getId());
        payment.setMembershipId(null);
        payment.setAmount(price);
        payment.setStatus("SUCCESS");
        paymentRepository.save(payment);

        // Also create trainer-client relation for convenience if not exists
        if (!trainerClientRepository.existsByTrainerIdAndClientId(trainerId, client.getId())) {
            TrainerClient relation = new TrainerClient();
            relation.setTrainerId(trainerId);
            relation.setClientId(client.getId());
            trainerClientRepository.save(relation);
        }

        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/{id}/choose")
    @Transactional
    public ResponseEntity<?> resignTrainer(@PathVariable("id") Integer trainerId, Authentication authentication) {
        String email = authentication.getName();
        AppUser client = appUserRepository.findByEmail(email).orElse(null);
        if (client == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new AuthErrorResponse("Client not found"));
        }

        var rel = trainerRentalRepository.findAllByClientId(client.getId())
                .stream()
                .filter(r -> "ACTIVE".equalsIgnoreCase(r.getStatus()) && r.getTrainerId().equals(trainerId))
                .findFirst();

        if (rel.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new AuthErrorResponse("Trainer rental not found"));
        }

        var rental = rel.get();
        rental.setStatus("EXPIRED");
        trainerRentalRepository.save(rental);

        // remove trainer-client relation if exists
        if (trainerClientRepository.existsByTrainerIdAndClientId(trainerId, client.getId())) {
            trainerClientRepository.deleteByTrainerIdAndClientId(trainerId, client.getId());
        }

        return ResponseEntity.noContent().build();
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
