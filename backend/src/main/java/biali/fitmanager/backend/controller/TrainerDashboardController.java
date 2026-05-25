package biali.fitmanager.backend.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/trainer")
public class TrainerDashboardController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public record CreateProgressRequest(int clientId, Double weight, String notes) {}
    public record CreateSessionRequest(int clientId, String title, String startTime, int durationMinutes) {}
    public record AddSessionExerciseRequest(int exerciseId, int sets, int reps, Double weight) {}

    public record ProgressLogDto(int id, String clientName, String logDate, Double weight, String notes) {}
    public record TrainingSessionDto(int id, String title, String date, String duration, String clientName, String status) {}
    public record ExerciseDto(int id, String name, String bodyPart) {}
    public record SessionExerciseDto(int id, int sessionId, String exerciseName, int sets, int reps, Double weight) {}
    public record ClientWorkoutDto(int id, String clientName, String exerciseName, Double weight, int sets, int reps, String date, Integer sessionId) {}

    @GetMapping("/progress")
    public ResponseEntity<List<ProgressLogDto>> getProgress(Principal principal) {
        String email = principal.getName();
        String sql = "SELECT pl.id, u.first_name || ' ' || u.last_name as clientName, " +
                     "TO_CHAR(pl.log_date, 'DD.MM.YYYY') as logDate, pl.weight, pl.notes " +
                     "FROM progress_logs pl " +
                     "JOIN users u ON pl.client_id = u.id " +
                     "JOIN users t ON pl.trainer_id = t.id " +
                     "WHERE t.email = ?";
        List<ProgressLogDto> logs = jdbcTemplate.query(sql, (rs, rowNum) -> new ProgressLogDto(
                rs.getInt("id"), rs.getString("clientName"), rs.getString("logDate"),
                rs.getDouble("weight"), rs.getString("notes")), email);
        return ResponseEntity.ok(logs);
    }

    @GetMapping("/sessions")
    public ResponseEntity<List<TrainingSessionDto>> getSessions(Principal principal) {
        String email = principal.getName();
        String sql = "SELECT ts.id, ts.title, TO_CHAR(ts.start_time, 'DD.MM.YYYY HH24:MI') as date, " +
                     "CAST(EXTRACT(EPOCH FROM (ts.end_time - ts.start_time))/60 AS INTEGER) || ' min' as duration, " +
                     "u.first_name || ' ' || u.last_name as clientName, r.status as status " +
                     "FROM training_sessions ts " +
                     "JOIN users t ON ts.trainer_id = t.id " +
                     "LEFT JOIN reservations r ON ts.id = r.session_id " +
                     "LEFT JOIN users u ON r.user_id = u.id " +
                     "WHERE t.email = ? ORDER BY ts.start_time DESC";
        List<TrainingSessionDto> sessions = jdbcTemplate.query(sql, (rs, rowNum) -> new TrainingSessionDto(
                rs.getInt("id"), rs.getString("title"), rs.getString("date"), rs.getString("duration"), rs.getString("clientName"), rs.getString("status")), email);
        return ResponseEntity.ok(sessions);
    }

    @PostMapping("/progress")
    public ResponseEntity<?> addProgress(Principal principal, @RequestBody CreateProgressRequest req) {
        String email = principal.getName();
        Integer trainerId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Integer.class, email);
        String sql = "INSERT INTO progress_logs (client_id, trainer_id, log_date, weight, notes) VALUES (?, ?, CURRENT_DATE, ?, ?)";
        jdbcTemplate.update(sql, req.clientId(), trainerId, req.weight(), req.notes());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/sessions")
    public ResponseEntity<?> addSession(Principal principal, @RequestBody CreateSessionRequest req) {
        String email = principal.getName();
        Integer trainerId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Integer.class, email);
        String sql = "INSERT INTO training_sessions (trainer_id, title, start_time, end_time, max_participants) VALUES (?, ?, CAST(? AS TIMESTAMP), CAST(? AS TIMESTAMP) + (? * INTERVAL '1 minute'), 1) RETURNING id";
        Integer sessionId = jdbcTemplate.queryForObject(sql, Integer.class, trainerId, req.title(), req.startTime(), req.startTime(), req.durationMinutes());
        if (req.clientId() > 0 && sessionId != null) {
            jdbcTemplate.update("INSERT INTO reservations (user_id, session_id, status) VALUES (?, ?, 'DRAFT')", req.clientId(), sessionId);
        }
        return ResponseEntity.ok().build();
    }

    @PostMapping("/sessions/{sessionId}/send")
    public ResponseEntity<?> sendSessionToClient(@PathVariable int sessionId) {
        jdbcTemplate.update("UPDATE reservations SET status = 'CONFIRMED' WHERE session_id = ? AND status = 'DRAFT'", sessionId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/exercises")
    public ResponseEntity<List<ExerciseDto>> getExercises() {
        String sql = "SELECT id, name, body_part FROM exercises ORDER BY body_part, name";
        List<ExerciseDto> exercises = jdbcTemplate.query(sql, (rs, rowNum) -> new ExerciseDto(
                rs.getInt("id"), rs.getString("name"), rs.getString("body_part")));
        return ResponseEntity.ok(exercises);
    }

    @GetMapping("/sessions/exercises")
    public ResponseEntity<List<SessionExerciseDto>> getAllSessionExercises(Principal principal) {
        String email = principal.getName();
        String sql = "SELECT se.id, se.session_id, e.name as exerciseName, se.sets, se.reps, se.weight " +
                     "FROM session_exercises se JOIN exercises e ON se.exercise_id = e.id " +
                     "JOIN training_sessions ts ON se.session_id = ts.id JOIN users t ON ts.trainer_id = t.id " +
                     "WHERE t.email = ?";
        List<SessionExerciseDto> sessionExercises = jdbcTemplate.query(sql, (rs, rowNum) -> new SessionExerciseDto(
                rs.getInt("id"), rs.getInt("session_id"), rs.getString("exerciseName"),
                rs.getInt("sets"), rs.getInt("reps"), rs.getDouble("weight")), email);
        return ResponseEntity.ok(sessionExercises);
    }

    @PostMapping("/sessions/{sessionId}/exercises")
    public ResponseEntity<?> addSessionExercise(@PathVariable int sessionId, @RequestBody AddSessionExerciseRequest req) {
        String sql = "INSERT INTO session_exercises (session_id, exercise_id, sets, reps, weight) VALUES (?, ?, ?, ?, ?)";
        jdbcTemplate.update(sql, sessionId, req.exerciseId(), req.sets(), req.reps(), req.weight());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/sessions/exercises/{id}")
    public ResponseEntity<?> deleteSessionExercise(@PathVariable int id) {
        jdbcTemplate.update("DELETE FROM session_exercises WHERE id = ?", id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/client-workouts")
    public ResponseEntity<List<ClientWorkoutDto>> getClientWorkouts(Principal principal) {
        String email = principal.getName();
        String sql = "SELECT cw.id, u.first_name || ' ' || u.last_name as clientName, e.name as exerciseName, cw.weight, cw.sets, cw.reps, TO_CHAR(cw.workout_date, 'DD.MM.YYYY') as date, cw.session_id as sessionId " +
                     "FROM client_workouts cw JOIN users u ON cw.client_id = u.id " +
                     "JOIN exercises e ON cw.exercise_id = e.id " +
                     "JOIN trainer_clients tc ON u.id = tc.client_id " +
                     "JOIN users t ON tc.trainer_id = t.id WHERE t.email = ? ORDER BY cw.workout_date ASC";
        List<ClientWorkoutDto> workouts = jdbcTemplate.query(sql, (rs, rowNum) -> new ClientWorkoutDto(
                rs.getInt("id"), rs.getString("clientName"), rs.getString("exerciseName"), rs.getDouble("weight"), rs.getInt("sets"), rs.getInt("reps"), rs.getString("date"), (Integer) rs.getObject("sessionId")), email);
        return ResponseEntity.ok(workouts);
    }
}