package biali.fitmanager.backend.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.Principal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import biali.fitmanager.backend.controller.ClientWorkoutController.LogWeightRequest;
import biali.fitmanager.backend.controller.ClientWorkoutController.LogWorkoutRequest;
import biali.fitmanager.backend.dto.AuthErrorResponse;

/**
 * Testy jednostkowe {@link ClientWorkoutController}: logowanie wagi i treningów.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Testy kontrolera treningow klienta")
class ClientWorkoutControllerTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private ClientWorkoutController clientWorkoutController;

    @BeforeEach
    void setUp() {
        clientWorkoutController = new ClientWorkoutController();
        ReflectionTestUtils.setField(clientWorkoutController, "jdbcTemplate", jdbcTemplate);
    }

    /**
     * Weryfikuje, że ujemna waga ciała jest odrzucana przed zapisem do bazy.
     *
     * @param principal zalogowany klient (client@example.com)
     * @param req LogWeightRequest z wagą -10.0
     * @return status 400, {@link AuthErrorResponse} "Weight cannot be negative", brak INSERT
     */
    @Test
    @DisplayName("Logowanie wagi zwraca 400 dla ujemnej wartosci")
    void logWeightReturnsBadRequestForNegativeWeight() {
        Principal principal = () -> "client@example.com";

        ResponseEntity<?> response = clientWorkoutController.logWeight(
                principal,
                new LogWeightRequest(-10.0)
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Weight cannot be negative",
                assertInstanceOf(AuthErrorResponse.class, response.getBody()).getMessage());
        verify(jdbcTemplate, never()).update(anyString(), any(), any());
    }

    /**
     * Sprawdza akceptację wagi równej zero i zapis do bazy.
     *
     * @param principal zalogowany klient (client@example.com)
     * @param req LogWeightRequest z wagą 0.0
     * @return status 200, INSERT do progress_logs przez JdbcTemplate
     */
    @Test
    @DisplayName("Logowanie wagi akceptuje zero i zapisuje rekord")
    void logWeightAcceptsZeroAndPersistsRecord() {
        Principal principal = () -> "client@example.com";

        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("client@example.com")))
                .thenReturn(3);
        doReturn(1).when(jdbcTemplate).update(
                eq("INSERT INTO progress_logs (client_id, log_date, weight) VALUES (?, CURRENT_DATE, ?)"),
                eq(3),
                eq(0.0)
        );

        ResponseEntity<?> response = clientWorkoutController.logWeight(
                principal,
                new LogWeightRequest(0.0)
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(jdbcTemplate).update(
                eq("INSERT INTO progress_logs (client_id, log_date, weight) VALUES (?, CURRENT_DATE, ?)"),
                eq(3),
                eq(0.0)
        );
    }

    /**
     * Potwierdza odrzucenie ujemnego ciężaru ćwiczenia przy aktualizacji wpisu.
     *
     * @param principal zalogowany klient
     * @param id identyfikator wpisu treningu (12)
     * @param req LogWorkoutRequest z ujemnym ciężarem (-5.0)
     * @return status 400, brak UPDATE w client_workouts
     */
    @Test
    @DisplayName("Aktualizacja treningu zwraca 400 dla ujemnego ciezaru")
    void updateWorkoutReturnsBadRequestForNegativeWeight() {
        Principal principal = () -> "client@example.com";

        ResponseEntity<?> response = clientWorkoutController.updateWorkout(
                principal,
                12,
                new LogWorkoutRequest(1, -5.0, 3, 10, null)
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(jdbcTemplate, never()).update(
                eq("UPDATE client_workouts SET weight = ?, sets = ?, reps = ? WHERE id = ? AND client_id = ?"),
                any(), any(), any(), any(), any()
        );
    }
}
