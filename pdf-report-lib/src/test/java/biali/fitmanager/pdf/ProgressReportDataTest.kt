package biali.fitmanager.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressReportDataTest {

  @Test
  fun `change label logic via exercise rows`() {
    val data = ProgressReportData(
      daysSinceFirstTraining = 30,
      dateRange = "01.01.2026 - 31.01.2026",
      exercises = listOf(
        ExerciseProgressRow("Martwy ciąg", 60.0, 100.0),
        ExerciseProgressRow("Wiosłowanie", 20.0, 20.0)
      )
    )

    assertEquals(2, data.exercises.size)
    assertTrue(data.exercises.first().endWeightKg > data.exercises.first().startWeightKg)
  }
}
