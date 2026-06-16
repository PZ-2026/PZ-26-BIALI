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
import biali.fitmanager.backend.repository.PaymentRepository;
import biali.fitmanager.backend.model.TrainerClient;
import biali.fitmanager.backend.repository.AppUserRepository;
import biali.fitmanager.backend.repository.TrainerClientRepository;
import biali.fitmanager.backend.validation.InputValidator;

/**
 * Panel administracyjny: użytkownicy, trenerzy, raporty i przychody.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private static final String ROLE_TRAINER = "TRAINER";
    private static final String ROLE_CLIENT = "CLIENT";

    private final AppUserRepository appUserRepository;
    private final TrainerClientRepository trainerClientRepository;
    private final PasswordEncoder passwordEncoder;
    private final biali.fitmanager.backend.service.PdfReportService pdfReportService;
    private final PaymentRepository paymentRepository;

    public AdminController(AppUserRepository appUserRepository,
                           TrainerClientRepository trainerClientRepository,
                           PasswordEncoder passwordEncoder,
                           biali.fitmanager.backend.service.PdfReportService pdfReportService,
                           PaymentRepository paymentRepository) {
        this.appUserRepository = appUserRepository;
        this.trainerClientRepository = trainerClientRepository;
        this.passwordEncoder = passwordEncoder;
        this.pdfReportService = pdfReportService;
        this.paymentRepository = paymentRepository;
    }

    /**
     * Zwraca listę użytkowników, opcjonalnie filtrowaną po roli.
     *
     * @param role opcjonalny filtr: ADMIN, TRAINER lub CLIENT
     * @return lista {@link UserResponse}
     */
    @GetMapping("/users")
    public List<UserResponse> getUsers(@RequestParam(required = false) String role) {
        if (role != null && !role.isBlank()) {
            return appUserRepository.findAllByRole(role.trim().toUpperCase()).stream().map(this::toResponse).toList();
        }
        return appUserRepository.findAll().stream().map(this::toResponse).toList();
    }

    /**
     * Tworzy nowego użytkownika.
     *
     * @param request dane użytkownika (email, hasło, rola, imię, nazwisko)
     * @return 201 z {@link UserResponse}, 400 przy błędach walidacji, 409 gdy email już istnieje
     */
    @PostMapping("/users")
    public ResponseEntity<?> createUser(@RequestBody UserUpsertRequest request) {
        String validationError = InputValidator.validateUserCreate(request);
        if (validationError != null) {
            return ResponseEntity.badRequest().body(new AuthErrorResponse(validationError));
        }
        if (appUserRepository.existsByEmail(request.getEmail().trim().toLowerCase())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new AuthErrorResponse("Email already exists"));
        }

        AppUser user = new AppUser();
        applyCommonFields(user, request, true);
        AppUser saved = appUserRepository.save(user);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(saved));
    }

    /**
     * Aktualizuje istniejącego użytkownika.
     *
     * @param id identyfikator użytkownika
     * @param request pola do aktualizacji
     * @return 200 z {@link UserResponse}, 400/404/409 przy błędach
     */
    @PutMapping("/users/{id}")
    public ResponseEntity<?> updateUser(@PathVariable Integer id, @RequestBody UserUpsertRequest request) {
        String validationError = InputValidator.validateUserUpdate(request);
        if (validationError != null) {
            return ResponseEntity.badRequest().body(new AuthErrorResponse(validationError));
        }

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

    /**
     * Usuwa użytkownika po ID.
     *
     * @param id identyfikator użytkownika
     * @return 204 po sukcesie, 404 gdy użytkownik nie istnieje
     */
    @DeleteMapping("/users/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable Integer id) {
        if (!appUserRepository.existsById(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new AuthErrorResponse("User not found"));
        }
        appUserRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Zwraca listę wszystkich trenerów.
     *
     * @return lista {@link UserResponse} z rolą TRAINER
     */
    @GetMapping("/trainers")
    public List<UserResponse> getTrainers() {
        return appUserRepository.findAllByRole(ROLE_TRAINER).stream().map(this::toResponse).toList();
    }

    /**
     * Tworzy nowego trenera (wymusza rolę TRAINER).
     *
     * @param request dane trenera
     * @return 201 z {@link UserResponse}, 400/409 przy błędach
     */
    @PostMapping("/trainers")
    public ResponseEntity<?> createTrainer(@RequestBody UserUpsertRequest request) {
        request.setRole(ROLE_TRAINER);
        return createUser(request);
    }

    /**
     * Aktualizuje dane trenera.
     *
     * @param id identyfikator trenera
     * @param request pola do aktualizacji
     * @return 200 z {@link UserResponse}, 404 gdy trener nie istnieje, 409 przy konflikcie emaila
     */
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

    /**
     * Usuwa trenera po ID.
     *
     * @param id identyfikator trenera
     * @return 204 po sukcesie, 404 gdy trener nie istnieje
     */
    @DeleteMapping("/trainers/{id}")
    public ResponseEntity<?> deleteTrainer(@PathVariable Integer id) {
        if (!appUserRepository.existsByIdAndRole(id, ROLE_TRAINER)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new AuthErrorResponse("Trainer not found"));
        }
        appUserRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Zwraca klientów przypisanych do trenera.
     *
     * @param trainerId identyfikator trenera
     * @return 200 z listą {@link UserResponse}, 404 gdy trener nie istnieje
     */
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

    /**
     * Zwraca łączny przychód z udanych płatności za karnety.
     *
     * @return suma przychodów (Double, 0.0 gdy brak płatności)
     */
    @GetMapping("/revenue/memberships")
    public ResponseEntity<Double> getMembershipRevenue() {
        var total = paymentRepository.sumSuccessfulMembershipRevenue();
        return ResponseEntity.ok(total == null ? 0.0 : total.doubleValue());
    }

    /**
     * Generuje raport PDF z listą użytkowników.
     *
     * @param authentication zalogowany admin (używany jako autor raportu)
     * @return 200 z plikiem PDF (application/pdf)
     */
    @GetMapping(value = "/reports/users/pdf", produces = "application/pdf")
    public ResponseEntity<byte[]> usersReport(org.springframework.security.core.Authentication authentication) {
        String generatedBy = authentication == null ? null : authentication.getName();
        byte[] pdf = pdfReportService.generateUsersReport(generatedBy);
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_PDF);
        headers.setContentDisposition(org.springframework.http.ContentDisposition.attachment().filename("users-report.pdf").build());
        return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
    }

    /**
     * Przypisuje klienta do trenera.
     *
     * @param trainerId identyfikator trenera
     * @param clientId identyfikator klienta
     * @return 201 po sukcesie, 404 gdy trener/klient nie istnieje, 409 gdy relacja już istnieje
     */
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

    /**
     * Usuwa przypisanie klienta do trenera.
     *
     * @param trainerId identyfikator trenera
     * @param clientId identyfikator klienta
     * @return 204 po sukcesie, 404 gdy relacja nie istnieje
     */
    @DeleteMapping("/trainers/{trainerId}/clients/{clientId}")
    public ResponseEntity<?> unassignClientFromTrainer(@PathVariable Integer trainerId, @PathVariable Integer clientId) {
        if (!trainerClientRepository.existsByTrainerIdAndClientId(trainerId, clientId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new AuthErrorResponse("Trainer-client relation not found"));
        }

        trainerClientRepository.deleteByTrainerIdAndClientId(trainerId, clientId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Kopiuje pola z żądania do encji użytkownika.
     *
     * @param user encja do uzupełnienia
     * @param request dane z żądania
     * @param createMode true przy tworzeniu (ustawia domyślną rolę CLIENT)
     */
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
