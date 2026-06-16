package biali.fitmanager.backend.controller;

import biali.fitmanager.backend.dto.ProgressSummaryResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;

/**
 * Podsumowanie postępów treningowych (dane dla aplikacji mobilnej).
 */
@RestController
@RequestMapping("/api/progress")
public class ProgressController {

    /**
     * Zwraca podsumowanie postępów użytkownika z danymi do wykresów.
     *
     * @return 200 z {@link ProgressSummaryResponse} (daysSinceFirstTraining, dateRange, progressList, chartData)
     */
    @GetMapping("/summary")
    public ResponseEntity<ProgressSummaryResponse> getProgressSummary() {
        
        
        ProgressSummaryResponse response = new ProgressSummaryResponse();
        response.setDaysSinceFirstTraining(120);
        response.setDateRange("09.11.2025 r. - 17.03.2026 r.");
        
        response.setProgressList(Arrays.asList(
            new ProgressSummaryResponse.ExerciseProgress("Podciągnięcia nachwytem", 0, 10),
            new ProgressSummaryResponse.ExerciseProgress("Martwy ciąg", 60, 100),
            new ProgressSummaryResponse.ExerciseProgress("Wiosłowanie", 20, 60),
            new ProgressSummaryResponse.ExerciseProgress("Dipy", 0, 10)
        ));

        // Przykładowe dane do wygenerowania wykresu słupkowego
        response.setChartData(Arrays.asList(50, 30, 40, 25, 60, 80));

        return ResponseEntity.ok(response);
    }
}