package biali.fitmanager

import biali.fitmanager.network.ClientProgressLogDto
import biali.fitmanager.network.ProgressSummaryResponse
import biali.fitmanager.pdf.ExerciseProgressRow
import biali.fitmanager.pdf.ProgressReportData
import biali.fitmanager.pdf.WeightLogEntry
import biali.fitmanager.pdf.WorkoutLogEntry

object ProgressReportMapper {
    fun from(
        summary: ProgressSummaryResponse,
        workouts: List<ClientWorkoutDto>,
        weightLogs: List<ClientProgressLogDto>
    ): ProgressReportData {
        return ProgressReportData(
            daysSinceFirstTraining = summary.daysSinceFirstTraining,
            dateRange = summary.dateRange,
            exercises = summary.progressList.map { exercise ->
                ExerciseProgressRow(
                    exerciseName = exercise.exerciseName,
                    startWeightKg = exercise.startWeight.toDouble(),
                    endWeightKg = exercise.endWeight.toDouble()
                )
            },
            workouts = workouts.map { workout ->
                WorkoutLogEntry(
                    date = workout.date,
                    exerciseName = workout.exerciseName,
                    sets = workout.sets,
                    reps = workout.reps,
                    weightKg = workout.weight
                )
            },
            weightLogs = weightLogs.map { log ->
                WeightLogEntry(
                    date = log.logDate,
                    weightKg = log.weight,
                    note = log.notes
                )
            }
        )
    }
}
