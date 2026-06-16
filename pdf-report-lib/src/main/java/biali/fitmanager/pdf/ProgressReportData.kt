package biali.fitmanager.pdf

/**
 * Dane wejściowe do raportu PDF. Aplikacja mapuje tu swoje modele z API.
 */
data class ProgressReportData(
    val daysSinceFirstTraining: Int,
    val dateRange: String,
    val exercises: List<ExerciseProgressRow> = emptyList(),
    val workouts: List<WorkoutLogEntry> = emptyList(),
    val weightLogs: List<WeightLogEntry> = emptyList(),
    val title: String = "Raport postępów",
    val subtitle: String = "Podsumowanie Twoich postępów treningowych",
    val footerText: String = "Wygenerowano przez FitManager"
)

data class ExerciseProgressRow(
    val exerciseName: String,
    val startWeightKg: Double,
    val endWeightKg: Double
)

data class WorkoutLogEntry(
    val date: String,
    val exerciseName: String,
    val sets: Int,
    val reps: Int,
    val weightKg: Double
)

data class WeightLogEntry(
    val date: String,
    val weightKg: Double,
    val note: String? = null
)
