package biali.fitmanager

object ProgressReportData {

    fun fromTrainerClient(
        clientName: String,
        logs: List<ClientProgressLog>,
        workouts: List<ClientWorkoutDto>,
        sessions: List<ClientTrainingSession>
    ): ProgressReportInput = ProgressReportInput(
        reportTitle = "Raport postępów podopiecznego",
        subjectName = clientName,
        weightEntries = logs.map { ProgressReportWeightEntry(it.logDate, it.weight, it.notes) },
        workouts = workouts.map {
            ProgressReportWorkoutEntry(it.date, it.exerciseName, it.sets, it.reps, it.weight, it.sessionId)
        },
        sessions = sessions.map {
            ProgressReportSessionEntry(it.title, it.date, it.status ?: "")
        }
    )

    fun fromClient(
        displayName: String,
        logs: List<biali.fitmanager.network.ClientProgressLogDto>,
        workouts: List<ClientWorkoutDto>
    ): ProgressReportInput = ProgressReportInput(
        reportTitle = "Raport moich postępów",
        subjectName = displayName,
        weightEntries = logs.map { ProgressReportWeightEntry(it.logDate, it.weight, it.notes) },
        workouts = workouts.map {
            ProgressReportWorkoutEntry(it.date, it.exerciseName, it.sets, it.reps, it.weight, it.sessionId)
        }
    )
}
