package biali.fitmanager

object ExercisePlanHelper {
    fun isTimeBased(exerciseName: String): Boolean {
        return exerciseName.contains("Deska", ignoreCase = true)
            || exerciseName.contains("Plank", ignoreCase = true)
    }

    /** 1 → seria, 2-4 → serie, reszta → serii */
    fun polishSeries(count: Int): String {
        val mod10 = count % 10
        val mod100 = count % 100
        return when {
            count == 1 -> "seria"
            mod10 in 2..4 && mod100 !in 12..14 -> "serie"
            else -> "serii"
        }
    }

    fun formatPlan(exerciseName: String, sets: Int, reps: Int, weight: Double): String {
        val seriesLabel = polishSeries(sets)
        return if (isTimeBased(exerciseName)) {
            "$sets $seriesLabel × $reps sek."
        } else if (weight > 0) {
            "$sets $seriesLabel × $reps powt. @ ${formatWeight(weight)} kg"
        } else {
            "$sets $seriesLabel × $reps powt."
        }
    }

    fun formatWeight(weight: Double): String {
        return if (weight == weight.toLong().toDouble()) weight.toLong().toString() else weight.toString()
    }

    fun formatDisplayDate(dateString: String?): String {
        if (dateString.isNullOrBlank() || dateString.equals("null", ignoreCase = true)) return ""
        var s = dateString.trim()

        if (Regex("^\\d{2}\\.\\d{2}\\.\\d{4}$").matches(s)) return s

        if (s.contains(" ")) s = s.substringBefore(" ")

        Regex("^(\\d{4})-(\\d{2})-(\\d{2})$").find(s)?.destructured?.let { (year, month, day) ->
            return "$day.$month.$year"
        }
        Regex("^(\\d{4})/(\\d{2})/(\\d{2})$").find(s)?.destructured?.let { (year, month, day) ->
            return "$day.$month.$year"
        }
        Regex("^(\\d{2})/(\\d{2})/(\\d{4})$").find(s)?.destructured?.let { (day, month, year) ->
            return "$day.$month.$year"
        }

        return s
    }
}
