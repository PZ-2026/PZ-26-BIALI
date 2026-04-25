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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import biali.fitmanager.backend.dto.AuthErrorResponse;
import biali.fitmanager.backend.dto.ReservationUpsertRequest;
import biali.fitmanager.backend.model.Reservation;
import biali.fitmanager.backend.repository.AppUserRepository;
import biali.fitmanager.backend.repository.ReservationRepository;
import biali.fitmanager.backend.repository.TrainingSessionRepository;

@RestController
@RequestMapping("/api/reservations")
public class ReservationController {

    private final ReservationRepository reservationRepository;
    private final AppUserRepository appUserRepository;
    private final TrainingSessionRepository trainingSessionRepository;

    public ReservationController(ReservationRepository reservationRepository,
                                 AppUserRepository appUserRepository,
                                 TrainingSessionRepository trainingSessionRepository) {
        this.reservationRepository = reservationRepository;
        this.appUserRepository = appUserRepository;
        this.trainingSessionRepository = trainingSessionRepository;
    }

    @GetMapping
    public List<Reservation> getReservations(@RequestParam(required = false) Integer userId,
                                             @RequestParam(required = false) Integer sessionId) {
        if (userId != null) {
            return reservationRepository.findAllByUserId(userId);
        }
        if (sessionId != null) {
            return reservationRepository.findAllBySessionId(sessionId);
        }
        return reservationRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getReservation(@PathVariable Integer id) {
        return reservationRepository.findById(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new AuthErrorResponse("Reservation not found")));
    }

    @PostMapping
    public ResponseEntity<?> createReservation(@RequestBody ReservationUpsertRequest request) {
        String validationError = validateRequest(request, true);
        if (validationError != null) {
            return ResponseEntity.badRequest().body(new AuthErrorResponse(validationError));
        }

        Reservation reservation = new Reservation();
        fillReservation(reservation, request);
        Reservation saved = reservationRepository.save(reservation);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateReservation(@PathVariable Integer id, @RequestBody ReservationUpsertRequest request) {
        return reservationRepository.findById(id)
                .<ResponseEntity<?>>map(existing -> {
                    String validationError = validateRequest(request, false);
                    if (validationError != null) {
                        return ResponseEntity.badRequest().body(new AuthErrorResponse(validationError));
                    }

                    fillReservation(existing, request);
                    return ResponseEntity.ok(reservationRepository.save(existing));
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new AuthErrorResponse("Reservation not found")));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteReservation(@PathVariable Integer id) {
        if (!reservationRepository.existsById(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new AuthErrorResponse("Reservation not found"));
        }
        reservationRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private String validateRequest(ReservationUpsertRequest request, boolean checkUniqueness) {
        if (request == null
                || request.getUserId() == null
                || request.getSessionId() == null
                || request.getStatus() == null || request.getStatus().isBlank()) {
            return "Invalid reservation payload";
        }

        if (!appUserRepository.existsById(request.getUserId())) {
            return "User does not exist";
        }

        if (!trainingSessionRepository.existsById(request.getSessionId())) {
            return "Session does not exist";
        }

        if (checkUniqueness && reservationRepository.findByUserIdAndSessionId(request.getUserId(), request.getSessionId()).isPresent()) {
            return "Reservation already exists for this user and session";
        }

        return null;
    }

    private void fillReservation(Reservation reservation, ReservationUpsertRequest request) {
        reservation.setUserId(request.getUserId());
        reservation.setSessionId(request.getSessionId());
        reservation.setStatus(request.getStatus().trim().toUpperCase());
    }
}
