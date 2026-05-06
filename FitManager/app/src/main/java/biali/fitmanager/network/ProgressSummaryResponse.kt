package biali.fitmanager.network

import com.google.gson.annotations.SerializedName

data class ProgressSummaryResponse(
    @SerializedName("daysSinceFirstTraining")
    val daysSinceFirstTraining: Int,

    @SerializedName("dateRange")
    val dateRange: String,

    @SerializedName("progressList")
    val progressList: List<ExerciseProgress>,

    @SerializedName("chartData")
    val chartData: List<Int>
)

data class ExerciseProgress(
    @SerializedName("exerciseName")
    val exerciseName: String,

    @SerializedName("startWeight")
    val startWeight: Int,

    @SerializedName("endWeight")
    val endWeight: Int
)