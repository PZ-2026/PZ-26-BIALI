package biali.fitmanager.pdf

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument

internal class ProgressPdfRenderer(private val document: PdfDocument) {

  private val writer = PdfPageWriter(document)

  fun render(data: ProgressReportData) {
    writer.drawTitle(data.title, data.subtitle)

    writer.drawSection("Statystyki")
    writer.drawLine("Dni od pierwszego treningu: ${data.daysSinceFirstTraining}")
    writer.drawLine("Okres: ${data.dateRange}")
    writer.drawLine("Liczba zapisanych treningów: ${data.workouts.size}")
    writer.drawLine("Liczba pomiarów wagi: ${data.weightLogs.size}")
    writer.blank(12f)

    writer.drawSection("Progresja ćwiczeń")
    val exerciseRows = buildExerciseRows(data)

    if (exerciseRows.isEmpty()) {
      writer.drawLine("Brak danych o progresji ćwiczeń.")
    } else {
      writer.drawTable(
        headers = listOf("Ćwiczenie", "Start", "Koniec", "Zmiana"),
        rows = exerciseRows
      )
      writer.blank(16f)
      writer.drawBarChart(
        title = "Wykres końcowych ciężarów (kg)",
        labels = exerciseRows.map { it[0].take(18) },
        values = exerciseRows.map { row ->
          row[2].replace(" kg", "").toFloatOrNull() ?: 0f
        }
      )
    }

    if (data.workouts.isNotEmpty()) {
      writer.blank(16f)
      writer.drawSection("Historia treningów")
      writer.drawTable(
        headers = listOf("Data", "Ćwiczenie", "Serie", "Powt.", "kg"),
        rows = data.workouts.sortedBy { it.date }.map { workout ->
          listOf(
            ReportDateFormatter.format(workout.date),
            workout.exerciseName,
            workout.sets.toString(),
            workout.reps.toString(),
            workout.weightKg.toString()
          )
        }
      )
    }

    if (data.weightLogs.isNotEmpty()) {
      writer.blank(16f)
      writer.drawSection("Historia wagi")
      writer.drawTable(
        headers = listOf("Data", "Waga (kg)", "Notatka"),
        rows = data.weightLogs.map { log ->
          listOf(
            ReportDateFormatter.format(log.date),
            log.weightKg.toString(),
            log.note.orEmpty().ifBlank { "-" }
          )
        }
      )
    }

    writer.drawFooter(data.footerText)
    writer.finish()
  }

  private fun buildExerciseRows(data: ProgressReportData): List<List<String>> {
    if (data.exercises.isNotEmpty()) {
      return data.exercises.map { exercise ->
        listOf(
          exercise.exerciseName,
          "${exercise.startWeightKg.toInt()} kg",
          "${exercise.endWeightKg.toInt()} kg",
          changeLabel(exercise.startWeightKg, exercise.endWeightKg)
        )
      }
    }

    return data.workouts
      .groupBy { it.exerciseName }
      .map { (name, logs) ->
        val first = logs.first().weightKg
        val last = logs.last().weightKg
        listOf(
          name,
          "${first.toInt()} kg",
          "${last.toInt()} kg",
          changeLabel(first, last)
        )
      }
  }

  private fun changeLabel(start: Double, end: Double): String {
    val delta = (end - start).toInt()
    return if (delta > 0) "+$delta kg" else "0 kg"
  }
}

internal class PdfPageWriter(private val document: PdfDocument) {
  private var pageNumber = 0
  private lateinit var page: PdfDocument.Page
  private lateinit var canvas: Canvas
  private var y = 50f

  private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = android.graphics.Color.BLACK
    textSize = 22f
    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
  }
  private val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = android.graphics.Color.BLACK
    textSize = 16f
    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
  }
  private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = android.graphics.Color.BLACK
    textSize = 12f
    typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
  }
  private val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = android.graphics.Color.WHITE
    textSize = 11f
    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
  }
  private val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = android.graphics.Color.GRAY
    textSize = 10f
  }
  private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)

  init {
    newPage()
  }

  fun drawTitle(title: String, subtitle: String) {
    canvas.drawText(title, 50f, y, titlePaint)
    y += 28f
    bodyPaint.textSize = 14f
    canvas.drawText(subtitle, 50f, y, bodyPaint)
    bodyPaint.textSize = 12f
    y += 30f
  }

  fun drawSection(title: String) {
    ensureSpace(28f)
    canvas.drawText(title, 50f, y, sectionPaint)
    y += 24f
  }

  fun drawLine(text: String) {
    ensureSpace(18f)
    canvas.drawText(text.take(90), 50f, y, bodyPaint)
    y += 18f
  }

  fun blank(height: Float) {
    y += height
    if (y > 780f) newPage()
  }

  fun drawTable(headers: List<String>, rows: List<List<String>>) {
    val columnWidths = listOf(130f, 110f, 70f, 70f, 70f)
    val visibleColumns = headers.size.coerceAtMost(columnWidths.size)
    val rowHeight = 22f

    fun drawHeaderRow() {
      ensureSpace(rowHeight + 8f)
      fillPaint.color = android.graphics.Color.rgb(0x4A, 0x6B, 0x5D)
      canvas.drawRect(50f, y - 14f, 545f, y + 8f, fillPaint)
      var x = 54f
      for (index in 0 until visibleColumns) {
        canvas.drawText(headers[index].take(16), x, y, headerPaint)
        x += columnWidths[index]
      }
      y += rowHeight
    }

    drawHeaderRow()
    rows.forEach { row ->
      if (y > 780f) {
        newPage()
        drawHeaderRow()
      }
      var x = 54f
      for (index in 0 until visibleColumns) {
        val cell = row.getOrElse(index) { "" }.take(20)
        canvas.drawText(cell, x, y, bodyPaint)
        x += columnWidths[index]
      }
      y += rowHeight
    }
  }

  fun drawBarChart(title: String, labels: List<String>, values: List<Float>) {
    if (values.isEmpty()) return

    drawSection(title)
    val chartTop = y
    val chartHeight = 140f
    val chartLeft = 70f
    val chartRight = 520f
    val chartBottom = chartTop + chartHeight
    ensureSpace(chartHeight + 40f)

    fillPaint.color = android.graphics.Color.LTGRAY
    canvas.drawRect(chartLeft, chartTop, chartRight, chartBottom, fillPaint)

    val maxValue = values.maxOrNull()?.coerceAtLeast(1f) ?: 1f
    val barCount = values.size.coerceAtMost(labels.size).coerceAtLeast(1)
    val slotWidth = (chartRight - chartLeft) / barCount
    val barWidth = slotWidth * 0.6f

    fillPaint.color = android.graphics.Color.rgb(0x00, 0xE6, 0x76)
    for (index in 0 until barCount) {
      val value = values[index]
      val barHeight = (value / maxValue) * (chartHeight - 20f)
      val left = chartLeft + index * slotWidth + (slotWidth - barWidth) / 2f
      val top = chartBottom - barHeight
      canvas.drawRect(left, top, left + barWidth, chartBottom, fillPaint)
      canvas.drawText(value.toInt().toString(), left, top - 4f, bodyPaint)
      canvas.drawText(labels[index], left, chartBottom + 14f, bodyPaint)
    }

    y = chartBottom + 28f
  }

  fun drawFooter(text: String) {
    canvas.drawText(text, 50f, 820f, footerPaint)
  }

  fun finish() {
    document.finishPage(page)
  }

  private fun ensureSpace(required: Float) {
    if (y + required > 780f) newPage()
  }

  private fun newPage() {
    if (pageNumber > 0) {
      document.finishPage(page)
    }
    pageNumber++
    page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNumber).create())
    canvas = page.canvas
    fillPaint.color = android.graphics.Color.WHITE
    canvas.drawRect(0f, 0f, 595f, 842f, fillPaint)
    y = 50f
  }
}
