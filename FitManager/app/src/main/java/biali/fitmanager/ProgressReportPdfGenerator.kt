package biali.fitmanager

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ProgressReportWeightEntry(val date: String, val weight: Double, val notes: String? = null)

data class ProgressReportWorkoutEntry(
    val date: String,
    val exerciseName: String,
    val sets: Int,
    val reps: Int,
    val weight: Double,
    val sessionId: Int? = null
)

data class ProgressReportSessionEntry(val title: String, val date: String, val status: String)

data class ProgressReportInput(
    val reportTitle: String,
    val subjectName: String,
    val weightEntries: List<ProgressReportWeightEntry>,
    val workouts: List<ProgressReportWorkoutEntry>,
    val sessions: List<ProgressReportSessionEntry> = emptyList()
)

object ProgressReportPdfGenerator {

    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 50f
    private const val BOTTOM = 800f

    fun write(context: Context, uri: Uri, input: ProgressReportInput) {
        try {
            val document = PdfDocument()
            val writer = PdfPageWriter(document)
            val paint = writer.paint

            drawHeader(writer, input)

            writer.sectionTitle("Podsumowanie")
            val completedSessions = input.sessions.count { it.status == "COMPLETED" }
            val pendingSessions = input.sessions.count { it.status == "CONFIRMED" }
            writer.bodyLine("Łącznie zapisanych ćwiczeń: ${input.workouts.size}")
            writer.bodyLine("Liczba pomiarów wagi: ${input.weightEntries.size}")
            if (input.sessions.isNotEmpty()) {
                writer.bodyLine("Treningi ukończone: $completedSessions | Oczekujące: $pendingSessions")
            }
            if (input.weightEntries.size >= 2) {
                val first = input.weightEntries.first().weight
                val last = input.weightEntries.last().weight
                val delta = last - first
                val sign = if (delta >= 0) "+" else ""
                writer.bodyLine("Zmiana wagi (pierwszy → ostatni pomiar): $sign${"%.1f".format(delta)} kg")
            }
            writer.gap(12f)

            if (input.weightEntries.isNotEmpty()) {
                writer.sectionTitle("Progresja wagi")
                drawWeightChart(writer, input.weightEntries)
                writer.gap(8f)
                writer.boldLine("Data          | Waga    | Notatka")
                input.weightEntries.forEach { e ->
                    val note = e.notes?.take(28)?.let { if ((e.notes?.length ?: 0) > 28) "$it…" else it } ?: "—"
                    writer.bodyLine("${pad(ExercisePlanHelper.formatDisplayDate(e.date), 14)} | ${pad("%.1f".format(e.weight) + " kg", 7)} | $note")
                }
                writer.gap(16f)
            }

            val grouped = input.workouts.groupBy { it.exerciseName }
            if (grouped.isNotEmpty()) {
                writer.sectionTitle("Progresja siły / wydolności (wg ćwiczenia)")
                grouped.forEach { (name, logs) ->
                    val workoutDtos = logs.map { w ->
                        ClientWorkoutDto(0, "", w.exerciseName, w.weight, w.sets, w.reps, w.date, w.sessionId)
                    }
                    val summary = ExerciseProgressMetrics.summaryForExercise(workoutDtos, name)
                    writer.boldLine(name)

                    if (summary.isTimeBased) {
                        val first = summary.snapshots.firstOrNull()
                        val last = summary.snapshots.lastOrNull()
                        first?.let {
                            writer.bodyLine("  Pierwszy trening: ${ExercisePlanHelper.formatDisplayDate(it.date)} — ${ExerciseProgressMetrics.formatVolumeLabel(name, it.volumeScore)}")
                        }
                        last?.let {
                            writer.bodyLine("  Ostatni trening: ${ExercisePlanHelper.formatDisplayDate(it.date)} — ${ExerciseProgressMetrics.formatVolumeLabel(name, it.volumeScore)}")
                        }
                        writer.bodyLine("  Rekord czasu (jedna seria): ${summary.maxWeightEver.toInt()} sek.")
                        drawExerciseLineChart(
                            writer,
                            summary.chartValues,
                            "sek.",
                            summary.snapshots.map { ExercisePlanHelper.formatDisplayDate(it.date) }
                        )
                    } else {
                        val first = summary.snapshots.firstOrNull()
                        val last = summary.snapshots.lastOrNull()
                        first?.let {
                            writer.bodyLine("  Start: ${ExercisePlanHelper.formatDisplayDate(it.date)} — ${ExerciseProgressMetrics.formatVolumeLabel(name, it.volumeScore)}")
                        }
                        last?.let {
                            writer.bodyLine("  Obecnie: ${ExercisePlanHelper.formatDisplayDate(it.date)} — ${ExerciseProgressMetrics.formatVolumeLabel(name, it.volumeScore)}")
                        }
                        if (summary.maxWeightEver > 0) {
                            writer.bodyLine("  Rekord ciężaru (niezależnie od powt.): ${ExercisePlanHelper.formatWeight(summary.maxWeightEver)} kg")
                        }
                        writer.bodyLine("  Najlepsza objętość: ${ExerciseProgressMetrics.formatVolumeLabel(name, summary.bestVolume)}")
                        drawExerciseLineChart(
                            writer,
                            summary.chartValues,
                            "kg·powt.",
                            summary.snapshots.map { ExercisePlanHelper.formatDisplayDate(it.date) }
                        )
                    }
                    writer.gap(8f)
                }
                writer.gap(8f)
            }

            if (input.sessions.isNotEmpty()) {
                writer.sectionTitle("Historia przypisanych treningów")
                writer.boldLine("Nazwa                    | Data       | Status")
                input.sessions.forEach { s ->
                    val status = when (s.status) {
                        "COMPLETED" -> "Zakończony"
                        "CONFIRMED" -> "Oczekuje"
                        "DRAFT" -> "Szkic"
                        else -> s.status
                    }
                    writer.bodyLine("${pad(s.title.take(24), 24)} | ${pad(ExercisePlanHelper.formatDisplayDate(s.date), 10)} | $status")
                }
                writer.gap(16f)
            }

            val dateStr = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date())
            writer.checkSpace(30f)
            paint.textSize = 9f
            paint.color = android.graphics.Color.GRAY
            writer.canvas.drawText("Wygenerowano: $dateStr | FitManager", MARGIN, PAGE_H - 30f, paint)
            paint.color = android.graphics.Color.BLACK

            writer.finishLastPage()
            context.contentResolver.openOutputStream(uri)?.use { document.writeTo(it) }
            document.close()
            Toast.makeText(context, "Raport zapisany do PDF!", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Błąd zapisu PDF: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun drawHeader(writer: PdfPageWriter, input: ProgressReportInput) {
        val paint = writer.paint
        paint.textSize = 22f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        writer.canvas.drawText(input.reportTitle, MARGIN, writer.y, paint)
        writer.y += 28f
        paint.textSize = 14f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        writer.canvas.drawText("Podopieczny: ${input.subjectName}", MARGIN, writer.y, paint)
        writer.y += 22f
        writer.gap(16f)
    }

    private fun formatChartValue(value: Float, unit: String): String =
        when {
            unit.equals("kg", ignoreCase = true) -> "%.1f".format(value)
            unit.contains("sek", ignoreCase = true) -> "${value.toInt()} s"
            unit.contains("powt", ignoreCase = true) -> ExerciseProgressMetrics.formatVolumeNumber(value.toDouble())
            else -> "%.1f".format(value)
        }

    private fun drawWeightChart(writer: PdfPageWriter, entries: List<ProgressReportWeightEntry>) {
        if (entries.isEmpty()) return
        val values = entries.map { it.weight.toFloat() }
        val xLabels = entries.map { ExercisePlanHelper.formatDisplayDate(it.date) }
        drawLineChart(
            writer = writer,
            values = values,
            unit = "kg",
            xLabels = xLabels,
            lineColor = android.graphics.Color.rgb(0x1E, 0x88, 0xE5),
            chartHeight = 80f
        )
    }

    private fun drawExerciseLineChart(
        writer: PdfPageWriter,
        values: List<Float>,
        unit: String,
        xLabels: List<String> = emptyList()
    ) {
        if (values.isEmpty()) return
        drawLineChart(
            writer = writer,
            values = values,
            unit = unit,
            xLabels = xLabels,
            lineColor = android.graphics.Color.rgb(0x00, 0xC8, 0x53),
            chartHeight = 72f
        )
    }

    private fun drawLineChart(
        writer: PdfPageWriter,
        values: List<Float>,
        unit: String,
        xLabels: List<String>,
        lineColor: Int,
        chartHeight: Float
    ) {
        val hasXLabels = xLabels.isNotEmpty()
        writer.checkSpace(chartHeight + if (hasXLabels) 36f else 24f)

        val maxV = (values.maxOrNull() ?: 1f).coerceAtLeast(1f)
        val minV = ((values.minOrNull() ?: 0f) - maxV * 0.08f).coerceAtLeast(0f)
        val range = (maxV - minV).coerceAtLeast(1f)
        val chartLeft = MARGIN + 28f
        val chartRight = PAGE_W - MARGIN
        val chartTop = writer.y + 10f
        val chartBottom = chartTop + chartHeight
        val paint = writer.paint

        paint.color = android.graphics.Color.LTGRAY
        paint.style = Paint.Style.STROKE
        writer.canvas.drawRect(chartLeft, chartTop, chartRight, chartBottom, paint)

        paint.style = Paint.Style.FILL
        paint.textSize = 7f
        paint.color = android.graphics.Color.GRAY
        writer.canvas.drawText(unit, MARGIN, chartTop - 2f, paint)
        writer.canvas.drawText(formatChartValue(maxV, unit), MARGIN, chartTop + 8f, paint)
        writer.canvas.drawText(formatChartValue(minV, unit), MARGIN, chartBottom - 2f, paint)

        repeat(3) { i ->
            val gridY = chartTop + chartHeight * i / 2f
            paint.color = android.graphics.Color.rgb(0xEE, 0xEE, 0xEE)
            paint.strokeWidth = 0.8f
            paint.style = Paint.Style.STROKE
            writer.canvas.drawLine(chartLeft, gridY, chartRight, gridY, paint)
            paint.style = Paint.Style.FILL
        }

        val step = if (values.size > 1) (chartRight - chartLeft) / (values.size - 1) else 0f
        var prevX = chartLeft
        var prevY = chartBottom - ((values.first() - minV) / range) * chartHeight

        paint.color = lineColor
        paint.strokeWidth = 2.5f
        paint.style = Paint.Style.STROKE
        values.forEachIndexed { i, v ->
            val x = if (values.size == 1) (chartLeft + chartRight) / 2f else chartLeft + i * step
            val y = chartBottom - ((v - minV) / range) * chartHeight
            if (i > 0) writer.canvas.drawLine(prevX, prevY, x, y, paint)

            paint.style = Paint.Style.FILL
            writer.canvas.drawCircle(x, y, 4.5f, paint)

            paint.textSize = 8f
            paint.color = android.graphics.Color.rgb(0x33, 0x33, 0x33)
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            val valueLabel = formatChartValue(v, unit)
            val labelW = paint.measureText(valueLabel)
            writer.canvas.drawText(valueLabel, x - labelW / 2f, (y - 7f).coerceAtLeast(chartTop + 6f), paint)

            xLabels.getOrNull(i)?.let { dateLabel ->
                paint.textSize = 6.5f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                paint.color = android.graphics.Color.DKGRAY
                val shortDate = dateLabel.take(8)
                val dateW = paint.measureText(shortDate)
                writer.canvas.drawText(shortDate, x - dateW / 2f, chartBottom + 11f, paint)
            }

            paint.color = lineColor
            paint.style = Paint.Style.STROKE
            prevX = x
            prevY = y
        }

        paint.style = Paint.Style.FILL
        paint.color = android.graphics.Color.BLACK
        paint.strokeWidth = 1f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        writer.y = chartBottom + if (hasXLabels) 22f else 14f
    }

    private fun pad(text: String, len: Int): String =
        if (text.length >= len) text else text + " ".repeat(len - text.length)

    private class PdfPageWriter(private val document: PdfDocument) {
        val paint = Paint()
        var canvas: Canvas
        var y = 60f
        private var pageNum = 1
        private var page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNum).create())

        init {
            canvas = page.canvas
            paint.color = android.graphics.Color.WHITE
            canvas.drawRect(0f, 0f, PAGE_W.toFloat(), PAGE_H.toFloat(), paint)
            paint.color = android.graphics.Color.BLACK
        }

        fun checkSpace(needed: Float) {
            if (y + needed > BOTTOM) newPage()
        }

        fun newPage() {
            document.finishPage(page)
            pageNum++
            page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNum).create())
            canvas = page.canvas
            paint.color = android.graphics.Color.WHITE
            canvas.drawRect(0f, 0f, PAGE_W.toFloat(), PAGE_H.toFloat(), paint)
            paint.color = android.graphics.Color.BLACK
            y = 60f
        }

        fun finishLastPage() = document.finishPage(page)

        fun sectionTitle(text: String) {
            checkSpace(36f)
            paint.textSize = 16f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText(text, MARGIN, y, paint)
            y += 24f
        }

        fun boldLine(text: String) {
            checkSpace(20f)
            paint.textSize = 11f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText(text, MARGIN, y, paint)
            y += 16f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }

        fun bodyLine(text: String) {
            checkSpace(18f)
            paint.textSize = 11f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText(text.take(90), MARGIN, y, paint)
            y += 15f
        }

        fun gap(amount: Float) {
            y += amount
        }
    }
}
