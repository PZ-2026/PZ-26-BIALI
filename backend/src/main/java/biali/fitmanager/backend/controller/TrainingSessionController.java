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
import biali.fitmanager.backend.dto.TrainingSessionUpsertRequest;
import biali.fitmanager.backend.model.TrainingSession;
import biali.fitmanager.backend.repository.AppUserRepository;
import biali.fitmanager.backend.repository.TrainingSessionRepository;

@RestController
@RequestMapping("/api/sessions")
public class TrainingSessionController {

    private final TrainingSessionRepository trainingSessionRepository;
    private final AppUserRepository appUserRepository;

    public TrainingSessionController(TrainingSessionRepository trainingSessionRepository,
                                     AppUserRepository appUserRepository) {
        this.trainingSessionRepository = trainingSessionRepository;
        this.appUserRepository = appUserRepository;
    }

    @GetMapping
    public List<TrainingSession> getSessions(@RequestParam(required = false) Integer trainerId) {
        if (trainerId != null) {
            return trainingSessionRepository.findAllByTrainerId(trainerId);
        }
        return trainingSessionRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getSession(@PathVariable Integer id) {
        return trainingSessionRepository.findById(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new AuthErrorResponse("Session not found")));
    }

    @PostMapping
    public ResponseEntity<?> createSession(@RequestBody TrainingSessionUpsertRequest request) {
        String validationError = validateRequest(request);
        if (validationError != null) {
            return ResponseEntity.badRequest().body(new AuthErrorResponse(validationError));
        }

        TrainingSession session = new TrainingSession();
        fillSession(session, request);
        TrainingSession saved = trainingSessionRepository.save(session);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateSession(@PathVariable Integer id, @RequestBody TrainingSessionUpsertRequest request) {
        return trainingSessionRepository.findById(id)
                .<ResponseEntity<?>>map(existing -> {
                    String validationError = validateRequest(request);
                    if (validationError != null) {
                        return ResponseEntity.badRequest().body(new AuthErrorResponse(validationError));
                    }

                    fillSession(existing, request);
                    return ResponseEntity.ok(trainingSessionRepository.save(existing));
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new AuthErrorResponse("Session not found")));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteSession(@PathVariable Integer id) {
        if (!trainingSessionRepository.existsById(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new AuthErrorResponse("Session not found"));
        }
        trainingSessionRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private String validateRequest(TrainingSessionUpsertRequest request) {
        if (request == null
                || request.getTrainerId() == null
                || request.getTitle() == null || request.getTitle().isBlank()
                || request.getStartTime() == null
                || request.getEndTime() == null
                || request.getMaxParticipants() == null
                || request.getMaxParticipants() < 1) {
            return "Invalid session payload";
        }

        if (!appUserRepository.existsById(request.getTrainerId())) {
            return "Trainer does not exist";
        }

        if (request.getEndTime().isBefore(request.getStartTime()) || request.getEndTime().isEqual(request.getStartTime())) {
            return "End time must be after start time";
        }

        return null;
    }

    private void fillSession(TrainingSession session, TrainingSessionUpsertRequest request) {
        session.setTrainerId(request.getTrainerId());
        session.setTitle(request.getTitle().trim());
        session.setStartTime(request.getStartTime());
        session.setEndTime(request.getEndTime());
        session.setMaxParticipants(request.getMaxParticipants());
    }
}
