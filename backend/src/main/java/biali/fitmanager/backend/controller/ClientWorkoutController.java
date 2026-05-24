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

    public record LogWorkoutRequest(int exerciseId, Double weight, int reps) {}
    public record ExerciseDto(int id, String name, String bodyPart) {}
    public record ClientWorkoutDto(int id, String clientName, String exerciseName, Double weight, int reps, String date) {}

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
        jdbcTemplate.update("INSERT INTO client_workouts (client_id, exercise_id, weight, reps, workout_date) VALUES (?, ?, ?, ?, CURRENT_DATE)",
                clientId, req.exerciseId(), req.weight(), req.reps());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/workouts")
    public ResponseEntity<List<ClientWorkoutDto>> getMyWorkouts(Principal principal) {
        String email = principal.getName();
        String sql = "SELECT cw.id, u.first_name || ' ' || u.last_name as clientName, e.name as exerciseName, cw.weight, cw.reps, TO_CHAR(cw.workout_date, 'DD.MM.YYYY') as date " +
                     "FROM client_workouts cw JOIN users u ON cw.client_id = u.id " +
                     "JOIN exercises e ON cw.exercise_id = e.id WHERE u.email = ? ORDER BY cw.workout_date ASC";
        List<ClientWorkoutDto> workouts = jdbcTemplate.query(sql, (rs, rowNum) -> new ClientWorkoutDto(
                rs.getInt("id"), rs.getString("clientName"), rs.getString("exerciseName"), rs.getDouble("weight"), rs.getInt("reps"), rs.getString("date")), email);
        return ResponseEntity.ok(workouts);
    }

    @DeleteMapping("/workouts/{id}")
    public ResponseEntity<?> deleteWorkout(Principal principal, @PathVariable int id) {
        String email = principal.getName();
        Integer clientId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Integer.class, email);
        jdbcTemplate.update("DELETE FROM client_workouts WHERE id = ? AND client_id = ?", id, clientId);
        return ResponseEntity.ok().build();
    }
}