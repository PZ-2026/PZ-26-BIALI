package biali.fitmanager.backend.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.List;

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

    @GetMapping("/exercises")
    public ResponseEntity<List<ExerciseDto>> getExercises() {
        String sql = "SELECT id, name, body_part FROM exercises ORDER BY body_part, name";
        List<ExerciseDto> exercises = jdbcTemplate.query(sql, (rs, rowNum) -> new ExerciseDto(
                rs.getInt("id"), rs.getString("name"), rs.getString("body_part")));
        return ResponseEntity.ok(exercises);
    }

    @PostMapping("/workouts")
    public ResponseEntity<?> logWorkout(Principal principal, @RequestBody LogWorkoutRequest req) {
        String email = principal.getName();
        Integer clientId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Integer.class, email);
        jdbcTemplate.update("INSERT INTO client_workouts (client_id, exercise_id, session_id, weight, sets, reps, workout_date) VALUES (?, ?, ?, ?, ?, ?, CURRENT_DATE)",
                clientId, req.exerciseId(), req.sessionId(), req.weight(), req.sets(), req.reps());
        return ResponseEntity.ok().build();
    }

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

    @DeleteMapping("/workouts/{id}")
    public ResponseEntity<?> deleteWorkout(Principal principal, @PathVariable int id) {
        String email = principal.getName();
        Integer clientId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Integer.class, email);
        jdbcTemplate.update("DELETE FROM client_workouts WHERE id = ? AND client_id = ?", id, clientId);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/workouts/{id}")
    public ResponseEntity<?> updateWorkout(Principal principal, @PathVariable int id, @RequestBody LogWorkoutRequest req) {
        String email = principal.getName();
        Integer clientId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Integer.class, email);
        jdbcTemplate.update("UPDATE client_workouts SET weight = ?, sets = ?, reps = ? WHERE id = ? AND client_id = ?",
                req.weight(), req.sets(), req.reps(), id, clientId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/sessions")
    public ResponseEntity<List<AssignedSessionDto>> getMySessions(Principal principal) {
        String email = principal.getName();
        String sql = "SELECT ts.id, ts.title, TO_CHAR(ts.start_time, 'DD.MM.YYYY HH24:MI') as date, " +
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

    @PostMapping("/sessions/{sessionId}/complete")
    public ResponseEntity<?> completeSession(Principal principal, @PathVariable int sessionId, @RequestBody CompleteSessionRequest req) {
        String email = principal.getName();
        Integer clientId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Integer.class, email);
        
        for (SetLogDto log : req.logs()) {
            jdbcTemplate.update("INSERT INTO client_workouts (client_id, exercise_id, session_id, weight, sets, reps, workout_date) VALUES (?, ?, ?, ?, ?, ?, CURRENT_DATE)",
                    clientId, log.exerciseId(), sessionId, log.weight(), log.setNumber(), log.reps());
        }
        
        jdbcTemplate.update("UPDATE reservations SET status = 'COMPLETED' WHERE user_id = ? AND session_id = ?", clientId, sessionId);
        return ResponseEntity.ok().build();
    }
}