package biali.fitmanager.backend.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import biali.fitmanager.backend.dto.AuthErrorResponse;
import biali.fitmanager.backend.validation.InputValidator;
import java.security.Principal;
import java.util.List;

/**
 * Panel klienta: ćwiczenia, treningi, postępy i sesje treningowe.
 */
@RestController
@RequestMapping("/api/client")
public class ClientWorkoutController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public record LogWorkoutRequest(int exerciseId, Double weight, int sets, int reps, Integer sessionId) {}
    public record ExerciseDto(int id, String name, String bodyPart) {}
    public record ClientWorkoutDto(int id, String clientName, String exerciseName, Double weight, int sets, int reps, String date, Integer sessionId) {}
    public record AssignedSessionExerciseDto(int exerciseId, String exerciseName, int sets, int reps, Double weight) {}
    public record AssignedSessionDto(int id, String title, String date, String duration, String trainerName, String status, List<AssignedSessionExerciseDto> exercises) {}
    public record SetLogDto(int exerciseId, int setNumber, Double weight, int reps) {}
    public record CompleteSessionRequest(List<SetLogDto> logs) {}
    public record LogWeightRequest(Double weight) {}
    public record ClientProgressLogDto(int id, String logDate, Double weight, String notes) {}

    /**
     * Zwraca katalog dostępnych ćwiczeń.
     *
     * @return lista {@link ExerciseDto} (id, name, bodyPart)
     */
    @GetMapping("/exercises")
    public ResponseEntity<List<ExerciseDto>> getExercises() {
        String sql = "SELECT id, name, body_part FROM exercises ORDER BY body_part, name";
        List<ExerciseDto> exercises = jdbcTemplate.query(sql, (rs, rowNum) -> new ExerciseDto(
                rs.getInt("id"), rs.getString("name"), rs.getString("body_part")));
        return ResponseEntity.ok(exercises);
    }

    /**
     * Zwraca historię pomiarów wagi zalogowanego klienta.
     *
     * @param principal zalogowany klient
     * @return lista {@link ClientProgressLogDto} (id, logDate, weight, notes)
     */
    @GetMapping("/progress")
    public ResponseEntity<List<ClientProgressLogDto>> getMyProgressLogs(Principal principal) {
        String email = principal.getName();
        String sql = "SELECT pl.id, TO_CHAR(pl.log_date, 'DD.MM.YYYY') as logDate, pl.weight, pl.notes " +
                     "FROM progress_logs pl JOIN users u ON pl.client_id = u.id " +
                     "WHERE u.email = ? ORDER BY pl.log_date ASC, pl.id ASC";
        List<ClientProgressLogDto> logs = jdbcTemplate.query(sql, (rs, rowNum) -> new ClientProgressLogDto(
                rs.getInt("id"), rs.getString("logDate"), rs.getDouble("weight"), rs.getString("notes")), email);
        return ResponseEntity.ok(logs);
    }

    /**
     * Zapisuje dzisiejszy pomiar wagi klienta.
     *
     * @param principal zalogowany klient
     * @param req waga (weight)
     * @return 200 po sukcesie, 400 przy błędnej walidacji
     */
    @PostMapping("/progress")
    public ResponseEntity<?> logWeight(Principal principal, @RequestBody LogWeightRequest req) {
        String weightError = InputValidator.validateWeight(req == null ? null : req.weight());
        if (weightError != null) {
            return ResponseEntity.badRequest().body(new AuthErrorResponse(weightError));
        }

        String email = principal.getName();
        Integer clientId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Integer.class, email);
        jdbcTemplate.update("INSERT INTO progress_logs (client_id, log_date, weight) VALUES (?, CURRENT_DATE, ?)", clientId, req.weight());
        return ResponseEntity.ok().build();
    }

    /**
     * Loguje wykonane ćwiczenie klienta.
     *
     * @param principal zalogowany klient
     * @param req exerciseId, weight, sets, reps, opcjonalnie sessionId
     * @return 200 po sukcesie, 400 przy błędnej walidacji
     */
    @PostMapping("/workouts")
    public ResponseEntity<?> logWorkout(Principal principal, @RequestBody LogWorkoutRequest req) {
        String weightError = InputValidator.validateWeight(req == null ? null : req.weight());
        if (weightError != null) {
            return ResponseEntity.badRequest().body(new AuthErrorResponse(weightError));
        }

        String email = principal.getName();
        Integer clientId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Integer.class, email);
        jdbcTemplate.update("INSERT INTO client_workouts (client_id, exercise_id, session_id, weight, sets, reps, workout_date) VALUES (?, ?, ?, ?, ?, ?, CURRENT_DATE)",
                clientId, req.exerciseId(), req.sessionId(), req.weight(), req.sets(), req.reps());
        return ResponseEntity.ok().build();
    }

    /**
     * Zwraca historię treningów zalogowanego klienta.
     *
     * @param principal zalogowany klient
     * @return lista {@link ClientWorkoutDto}
     */
    @GetMapping("/workouts")
    public ResponseEntity<List<ClientWorkoutDto>> getMyWorkouts(Principal principal) {
        String email = principal.getName();
        String sql = "SELECT cw.id, u.first_name || ' ' || u.last_name as clientName, e.name as exerciseName, cw.weight, cw.sets, cw.reps, TO_CHAR(cw.workout_date, 'DD.MM.YYYY') as date, cw.session_id as sessionId " +
                     "FROM client_workouts cw JOIN users u ON cw.client_id = u.id " +
                     "JOIN exercises e ON cw.exercise_id = e.id WHERE u.email = ? ORDER BY cw.workout_date ASC";
        List<ClientWorkoutDto> workouts = jdbcTemplate.query(sql, (rs, rowNum) -> new ClientWorkoutDto(
                rs.getInt("id"), rs.getString("clientName"), rs.getString("exerciseName"), rs.getDouble("weight"), rs.getInt("sets"), rs.getInt("reps"), rs.getString("date"), (Integer) rs.getObject("sessionId")), email);
        return ResponseEntity.ok(workouts);
    }

    /**
     * Usuwa wpis treningu klienta.
     *
     * @param principal zalogowany klient
     * @param id identyfikator wpisu treningu
     * @return 200 po sukcesie
     */
    @DeleteMapping("/workouts/{id}")
    public ResponseEntity<?> deleteWorkout(Principal principal, @PathVariable int id) {
        String email = principal.getName();
        Integer clientId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Integer.class, email);
        jdbcTemplate.update("DELETE FROM client_workouts WHERE id = ? AND client_id = ?", id, clientId);
        return ResponseEntity.ok().build();
    }

    /**
     * Aktualizuje wpis treningu klienta (ciężar, serie, powtórzenia).
     *
     * @param principal zalogowany klient
     * @param id identyfikator wpisu treningu
     * @param req nowe wartości weight, sets, reps
     * @return 200 po sukcesie, 400 przy błędnej walidacji
     */
    @PutMapping("/workouts/{id}")
    public ResponseEntity<?> updateWorkout(Principal principal, @PathVariable int id, @RequestBody LogWorkoutRequest req) {
        String weightError = InputValidator.validateWeight(req == null ? null : req.weight());
        if (weightError != null) {
            return ResponseEntity.badRequest().body(new AuthErrorResponse(weightError));
        }

        String email = principal.getName();
        Integer clientId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Integer.class, email);
        jdbcTemplate.update("UPDATE client_workouts SET weight = ?, sets = ?, reps = ? WHERE id = ? AND client_id = ?",
                req.weight(), req.sets(), req.reps(), id, clientId);
        return ResponseEntity.ok().build();
    }

    /**
     * Zwraca sesje treningowe przypisane zalogowanemu klientowi.
     *
     * @param principal zalogowany klient
     * @return lista {@link AssignedSessionDto} z ćwiczeniami i statusem rezerwacji
     */
    @GetMapping("/sessions")
    public ResponseEntity<List<AssignedSessionDto>> getMySessions(Principal principal) {
        String email = principal.getName();
        String sql = "SELECT ts.id, ts.title, TO_CHAR(ts.start_time, 'DD.MM.YYYY') as date, " +
                     "CAST(EXTRACT(EPOCH FROM (ts.end_time - ts.start_time))/60 AS INTEGER) || ' min' as duration, " +
                     "t.first_name || ' ' || t.last_name as trainerName, r.status as status " +
                     "FROM training_sessions ts " +
                     "JOIN reservations r ON ts.id = r.session_id " +
                     "JOIN users u ON r.user_id = u.id " +
                     "JOIN users t ON ts.trainer_id = t.id " +
                     "WHERE u.email = ? AND r.status != 'DRAFT' ORDER BY ts.start_time ASC";
        List<AssignedSessionDto> sessions = jdbcTemplate.query(sql, (rs, rowNum) -> {
            int sessionId = rs.getInt("id");
            String exSql = "SELECT e.id as exerciseId, e.name, se.sets, se.reps, se.weight FROM session_exercises se JOIN exercises e ON se.exercise_id = e.id WHERE se.session_id = ?";
            List<AssignedSessionExerciseDto> exercises = jdbcTemplate.query(exSql, (rsEx, rowNumEx) -> new AssignedSessionExerciseDto(rsEx.getInt("exerciseId"), rsEx.getString("name"), rsEx.getInt("sets"), rsEx.getInt("reps"), rsEx.getDouble("weight")), sessionId);
            return new AssignedSessionDto(sessionId, rs.getString("title"), rs.getString("date"), rs.getString("duration"), rs.getString("trainerName"), rs.getString("status"), exercises);
        }, email);
        return ResponseEntity.ok(sessions);
    }

    /**
     * Oznacza sesję jako ukończoną i zapisuje logi serii.
     *
     * @param principal zalogowany klient
     * @param sessionId identyfikator sesji
     * @param req lista logów serii ({@link SetLogDto})
     * @return 200 po sukcesie
     */
    @PostMapping("/sessions/{sessionId}/complete")
    public ResponseEntity<?> completeSession(Principal principal, @PathVariable int sessionId, @RequestBody CompleteSessionRequest req) {
        String email = principal.getName();
        Integer clientId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Integer.class, email);

        Integer confirmedCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM reservations WHERE user_id = ? AND session_id = ? AND status = 'CONFIRMED'",
                Integer.class, clientId, sessionId);
        if (confirmedCount == null || confirmedCount == 0) {
            return ResponseEntity.badRequest().body(new AuthErrorResponse("Ten trening nie jest dostępny do ukończenia."));
        }

        for (SetLogDto log : req.logs()) {
            jdbcTemplate.update("INSERT INTO client_workouts (client_id, exercise_id, session_id, weight, sets, reps, workout_date) VALUES (?, ?, ?, ?, ?, ?, CURRENT_DATE)",
                    clientId, log.exerciseId(), sessionId, log.weight(), log.setNumber(), log.reps());
        }
        
        jdbcTemplate.update("UPDATE reservations SET status = 'COMPLETED' WHERE user_id = ? AND session_id = ?", clientId, sessionId);
        return ResponseEntity.ok().build();
    }
}