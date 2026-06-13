package biali.fitmanager

import biali.fitmanager.network.AssignedSessionDto
import biali.fitmanager.network.SetLogDto

data class WorkoutSessionSnapshot(
    val date: String,
    val exerciseName: String,
    val setCount: Int,
    val volumeScore: Double,
    val maxWeight: Double,
    val bestHoldSeconds: Int
)

data class ExerciseProgressSummary(
    val exerciseName: String,
    val isTimeBased: Boolean,
    val snapshots: List<WorkoutSessionSnapshot>,
    val chartValues: List<Float>,
    val maxWeightEver: Double,
    val bestVolume: Double,
    val latestVolume: Double,
    val trendPercent: Float?
)

data class PersonalRecord(
    val exerciseName: String,
    val message: String
)

object ExerciseProgressMetrics {

    private fun sessionKey(workout: ClientWorkoutDto): String =
        if (workout.sessionId != null) "s${workout.sessionId}" else "d${workout.date}_${workout.exerciseName}"

    fun snapshotsForExercise(workouts: List<ClientWorkoutDto>, exerciseName: String): List<WorkoutSessionSnapshot> {
        return workouts
            .filter { it.exerciseName == exerciseName }
            .groupBy { sessionKey(it) }
            .map { (_, sets) -> buildSnapshotFromSets(exerciseName, sets) }
            .sortedBy { ExercisePlanHelper.formatDisplayDate(it.date) }
    }

    private fun buildSnapshotFromSets(exerciseName: String, sets: List<ClientWorkoutDto>): WorkoutSessionSnapshot {
        val date = sets.first().date
        val isTime = ExercisePlanHelper.isTimeBased(exerciseName)
        return if (isTime) {
            val totalSec = sets.sumOf { it.reps }
            val bestHold = sets.maxOfOrNull { it.reps } ?: 0
            WorkoutSessionSnapshot(
                date = date,
                exerciseName = exerciseName,
                setCount = sets.size,
                volumeScore = totalSec.toDouble(),
                maxWeight = bestHold.toDouble(),
                bestHoldSeconds = bestHold
            )
        } else {
            val volume = sets.sumOf { it.weight * it.reps }
            val maxW = sets.maxOfOrNull { it.weight } ?: 0.0
            WorkoutSessionSnapshot(
                date = date,
                exerciseName = exerciseName,
                setCount = sets.size,
                volumeScore = volume,
                maxWeight = maxW,
                bestHoldSeconds = 0
            )
        }
    }

    fun summaryForExercise(workouts: List<ClientWorkoutDto>, exerciseName: String): ExerciseProgressSummary {
        val isTime = ExercisePlanHelper.isTimeBased(exerciseName)
        val snapshots = snapshotsForExercise(workouts, exerciseName)
        val chartValues = snapshots.map { it.volumeScore.toFloat() }
        val maxWeightEver = if (isTime) {
            snapshots.maxOfOrNull { it.bestHoldSeconds.toDouble() } ?: 0.0
        } else {
            workouts.filter { it.exerciseName == exerciseName }.maxOfOrNull { it.weight } ?: 0.0
        }
        val bestVolume = snapshots.maxOfOrNull { it.volumeScore } ?: 0.0
        val latestVolume = snapshots.lastOrNull()?.volumeScore ?: 0.0
        val trendPercent = if (snapshots.size >= 2) {
            val first = snapshots.first().volumeScore
            val last = snapshots.last().volumeScore
            if (first > 0) ((last - first) / first * 100).toFloat() else null
        } else {
            null
        }
        return ExerciseProgressSummary(
            exerciseName = exerciseName,
            isTimeBased = isTime,
            snapshots = snapshots,
            chartValues = chartValues,
            maxWeightEver = maxWeightEver,
            bestVolume = bestVolume,
            latestVolume = latestVolume,
            trendPercent = trendPercent
        )
    }

    fun formatVolumeLabel(exerciseName: String, volume: Double): String =
        if (ExercisePlanHelper.isTimeBased(exerciseName)) {
            "${volume.toInt()} sek."
        } else {
            "${formatVolumeNumber(volume)} kg·powt."
        }

    fun formatVolumeNumber(volume: Double): String =
        if (volume == volume.toLong().toDouble()) volume.toLong().toString()
        else "%.0f".format(volume)

    fun maxRecordLabel(exerciseName: String, value: Double): String =
        if (ExercisePlanHelper.isTimeBased(exerciseName)) {
            "max ${value.toInt()} sek."
        } else if (value > 0) {
            "max ${ExercisePlanHelper.formatWeight(value)} kg"
        } else {
            ""
        }

    fun sessionVolumeLabel(exerciseName: String, sessionLogs: List<ClientWorkoutDto>): String {
        if (ExercisePlanHelper.isTimeBased(exerciseName)) {
            return "${sessionLogs.sumOf { it.reps }} sek. łącznie"
        }
        return formatVolumeLabel(exerciseName, sessionLogs.sumOf { it.weight * it.reps }.toDouble())
    }

    fun sessionGroupKey(log: ClientWorkoutDto): String =
        if (log.sessionId != null) "s${log.sessionId}" else "d${log.date}"

    fun chartAxisLabel(exerciseName: String): String =
        if (ExercisePlanHelper.isTimeBased(exerciseName)) "Łączny czas trzymania"
        else "Objętość (kg × powt.)"

    fun detectNewRecords(
        existingWorkouts: List<ClientWorkoutDto>,
        session: AssignedSessionDto,
        logs: List<SetLogDto>
    ): List<PersonalRecord> {
        val records = mutableListOf<PersonalRecord>()
        val exerciseMap = session.exercises.associateBy { it.exerciseId }

        logs.groupBy { it.exerciseId }.forEach { (exerciseId, setLogs) ->
            val exercise = exerciseMap[exerciseId] ?: return@forEach
            val name = exercise.exerciseName
            val isTime = ExercisePlanHelper.isTimeBased(name)
            val historical = snapshotsForExercise(existingWorkouts, name)

            if (isTime) {
                val newTotal = setLogs.sumOf { it.reps }.toDouble()
                val newBestHold = setLogs.maxOfOrNull { it.reps } ?: 0
                val bestTotal = historical.maxOfOrNull { it.volumeScore } ?: 0.0
                val bestHold = historical.maxOfOrNull { it.bestHoldSeconds } ?: 0

                if (setLogs.isNotEmpty() && newTotal > bestTotal) {
                    records.add(
                        PersonalRecord(
                            name,
                            "$name — nowy rekord: ${newTotal.toInt()} sek. łącznie!"
                        )
                    )
                }
                if (newBestHold > bestHold) {
                    records.add(
                        PersonalRecord(
                            name,
                            "$name — najdłuższa seria: $newBestHold sek.!"
                        )
                    )
                }
            } else {
                val newVolume = setLogs.sumOf { it.weight * it.reps }
                val newMaxWeight = setLogs.maxOfOrNull { it.weight } ?: 0.0
                val bestVolume = historical.maxOfOrNull { it.volumeScore } ?: 0.0
                val bestMaxWeight = existingWorkouts
                    .filter { it.exerciseName == name }
                    .maxOfOrNull { it.weight } ?: 0.0

                if (setLogs.isNotEmpty() && newVolume > bestVolume) {
                    records.add(
                        PersonalRecord(
                            name,
                            "$name — nowa objętość: ${formatVolumeNumber(newVolume)} kg·powt.!"
                        )
                    )
                }
                if (newMaxWeight > bestMaxWeight && newMaxWeight > 0) {
                    records.add(
                        PersonalRecord(
                            name,
                            "$name — rekord ciężaru: ${ExercisePlanHelper.formatWeight(newMaxWeight)} kg!"
                        )
                    )
                }
            }
        }
        return records
    }
}
