package biali.fitmanager

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import biali.fitmanager.network.ApiResult
import biali.fitmanager.network.FitManagerRepository
import biali.fitmanager.network.ClientProgressLogDto
import biali.fitmanager.network.ProgressSummaryResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LogWorkoutRequest(val exerciseId: Int, val weight: Double, val sets: Int, val reps: Int, val sessionId: Int? = null)
data class ClientWorkoutDto(val id: Int, val clientName: String, val exerciseName: String, val weight: Double, val sets: Int, val reps: Int, val date: String, val sessionId: Int? = null)

/**
 * Formatuje datę do postaci DD.MM.YYYY używając wyłącznie manipulacji na stringach.
 * Obsługuje formaty: DD.MM.YYYY (zwraca bez zmian), YYYY-MM-DD (konwertuje),
 * oraz inne formaty zawierające 8 cyfr (próbuje wyodrębnić dzień/miesiąc/rok).
 */
/**
 * Formatuje datę do postaci DD.MM.YYYY używając wyłącznie manipulacji na stringach.
 * Obsługuje formaty: DD.MM.YYYY (zwraca bez zmian), YYYY-MM-DD (konwertuje),
 * YYYY-MM-DD HH:MM:SS, oraz inne formaty zawierające 8 cyfr.
 */
private fun formatDisplayDate(dateString: String?): String {
    if (dateString.isNullOrBlank() || dateString.equals("null", ignoreCase = true)) return ""
    var s = dateString.trim()
    
    // Jeśli już w formacie DD.MM.YYYY – zwróć bez zmian
    if (Regex("^\\d{2}\\.\\d{2}\\.\\d{4}$").matches(s)) return s
    
    // Jeśli zawiera spację – weź tylko część przed spacją (odrzuć czas)
    if (s.contains(" ")) {
        s = s.substringBefore(" ")
    }
    
    // Jeśli w formacie YYYY-MM-DD – konwertuj na DD.MM.YYYY
    val isoMatch = Regex("^(\\d{4})-(\\d{2})-(\\d{2})$").find(s)
    if (isoMatch != null) {
        val (year, month, day) = isoMatch.destructured
        return "$day.$month.$year"
    }
    
    // Jeśli w formacie YYYY/MM/DD – konwertuj na DD.MM.YYYY
    val slashMatch = Regex("^(\\d{4})/(\\d{2})/(\\d{2})$").find(s)
    if (slashMatch != null) {
        val (year, month, day) = slashMatch.destructured
        return "$day.$month.$year"
    }
    
    // Jeśli w formacie DD/MM/YYYY – konwertuj na DD.MM.YYYY
    val euSlashMatch = Regex("^(\\d{2})/(\\d{2})/(\\d{4})$").find(s)
    if (euSlashMatch != null) {
        val (day, month, year) = euSlashMatch.destructured
        return "$day.$month.$year"
    }
    
    // Fallback: wyciągnij 8 cyfr i spróbuj zinterpretować
    val digits = s.replace(Regex("[^0-9]"), "")
    if (digits.length >= 8) {
        val d1 = digits.substring(0, 2).toIntOrNull() ?: 0
        val d2 = digits.substring(2, 4).toIntOrNull() ?: 0
        val d3 = digits.substring(4, 6).toIntOrNull() ?: 0
        val d4 = digits.substring(6, 8).toIntOrNull() ?: 0
        // yyyyMMdd
        if (d1 in 20..99 && d2 in 1..12 && d3 in 1..31) {
            val year = if (d1 < 100) 2000 + d1 else d1
            return "${d3.toString().padStart(2, '0')}.${d2.toString().padStart(2, '0')}.${year}"
        }
        // ddMMyyyy
        if (d3 in 20..99 && d2 in 1..12 && d1 in 1..31) {
            val year = if (d3 < 100) 2000 + d3 else 2000 + d3
            return "${d1.toString().padStart(2, '0')}.${d2.toString().padStart(2, '0')}.20${d3}"
        }
        // dd.MM.yyyy z 8 cyframi
        if (d1 in 1..31 && d2 in 1..12 && (d3 in 20..99 || d3 in 2020..2100)) {
            val year = if (d3 < 100) 2000 + d3 else d3
            return "${d1.toString().padStart(2, '0')}.${d2.toString().padStart(2, '0')}.$year"
        }
    }
    
    // Ostateczny fallback – zwróć oryginał
    return s
}

class ProgressActivity : ComponentActivity() {
    private val viewModel: ProgressViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        viewModel.fetchProgress()

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ProgressScreen(
                        viewModel = viewModel,
                        onBackClick = { finish() },
                        onNavigateToHome = ::navigateToHome,
                        onNavigateToTrainers = ::navigateToTrainers,
                        onNavigateToMemberships = ::navigateToMemberships,
                        onNavigateToAccount = ::navigateToAccount
                    )
                }
            }
        }
    }

    private fun navigateToHome() {
        startActivity(Intent(this, HomeActivity::class.java))
        finish()
    }

    private fun navigateToTrainers() {
        startActivity(Intent(this, TrainersActivity::class.java))
        finish()
    }

    private fun navigateToMemberships() {
        startActivity(Intent(this, MembershipsActivity::class.java))
        finish()
    }

    private fun navigateToAccount() {
        startActivity(Intent(this, AccountActivity::class.java))
        finish()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(
    viewModel: ProgressViewModel,
    onBackClick: () -> Unit,
    onNavigateToHome: () -> Unit,
    onNavigateToTrainers: () -> Unit,
    onNavigateToMemberships: () -> Unit,
    onNavigateToAccount: () -> Unit
) {

    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedExerciseName by remember { mutableStateOf<String?>(null) }
    var showWeightDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Twoje postępy", fontWeight = FontWeight.Bold, color = Color(0xFF4A6B5D)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wstecz", tint = Color(0xFF4A6B5D))
                    }
                }
            )
        },
        bottomBar = {
            FitBottomNav(
                currentRoute = "progress",
                onNavigateToHome = onNavigateToHome,
                onNavigateToTrainers = onNavigateToTrainers,
                onNavigateToMemberships = onNavigateToMemberships,
                onNavigateToProgress = { },
                onNavigateToAccount = onNavigateToAccount
            )
        }
    ) { paddingValues ->
      Column(modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp)) {

        // Sprawdzamy stan
        when {
            state.isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF00E676))
                }
            }
            state.error != null -> {
                Text(text = "Błąd: ${state.error}", color = MaterialTheme.colorScheme.error)
            }
            else -> {
                val workouts = state.myWorkouts
                val groupedWorkouts = workouts.groupBy { it.exerciseName }
                
                val progressLogs = state.progressLogs
                val latestLog = progressLogs.lastOrNull()

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Twoja waga", fontSize = 14.sp, color = Color.Gray)
                            if (latestLog != null) {
                                Text("${latestLog.weight} kg", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4A6B5D))
                                Text("Ostatni pomiar: ${formatDisplayDate(latestLog.logDate)}", fontSize = 12.sp, color = Color.Gray)
                            } else {
                                Text("Brak pomiarów", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
                            }
                        }
                        Button(
                            onClick = { showWeightDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676))
                        ) {
                            Text("Zapisz wagę", color = Color.White)
                        }
                    }
                    if (latestLog?.notes?.isNotBlank() == true) {
                        HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f))
                        Text(text = "Notatka trenera:\n${latestLog.notes}", fontSize = 13.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, modifier = Modifier.padding(16.dp))
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))

                if (workouts.isEmpty()) {
                    Text(
                        text = "Nie ukończyłeś jeszcze żadnego zaleconego treningu od trenera.",
                        color = Color.Gray,
                        fontSize = 16.sp
                    )
                } else {
                    Text(
                        text = "Masz za sobą łącznie ${workouts.size} zapisanych aktywności!",
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Analiza wyników",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4A6B5D)
                )

                // Tabela (Nagłówki)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Nazwa ćwiczenia", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("Ciężar", fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.5f))
                }

                // Wiersze z ćwiczeniami
                groupedWorkouts.forEach { (exerciseName, logs) ->
                    val firstWeight = logs.first().weight
                    val lastWeight = logs.last().weight

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { selectedExerciseName = exerciseName },
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = exerciseName, modifier = Modifier.weight(1f), color = Color.DarkGray, fontWeight = FontWeight.Medium)
                            Text(
                                text = "${firstWeight}kg -> ${lastWeight}kg",
                                modifier = Modifier.weight(0.5f),
                                color = Color.DarkGray,
                                fontWeight = FontWeight.SemiBold
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Pokaż wykres",
                                tint = Color(0xFF00E676),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Przycisk generowania raportu postępów - generuje PDF i otwiera natychmiast
                Button(
                    onClick = {
                        scope.launch {
                            val repo = FitManagerRepository()
                            when (val res = repo.getProgressSummary()) {
                                is ApiResult.Success -> {
                                    try {
                                        val pdfFile = withContext(Dispatchers.IO) {
                                            buildProgressPdfFile(
                                                context = context,
                                                summary = res.data,
                                                workouts = state.myWorkouts,
                                                weightLogs = state.progressLogs
                                            )
                                        }
                                        openPdfFile(context, pdfFile)
                                    } catch (e: Exception) {
                                        Toast.makeText(
                                            context,
                                            "Błąd podczas generowania PDF: ${e.message}",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                                is ApiResult.Unauthorized -> {
                                    Toast.makeText(context, "Sesja wygasła. Zaloguj się ponownie.", Toast.LENGTH_SHORT).show()
                                }
                                is ApiResult.Error -> {
                                    Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A6B5D))
                ) {
                    Text("Generuj raport postępów", color = Color.White)
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
      }

        // Wyświetlanie oddzielnego okienka z wykresem po kliknięciu w ćwiczenie
        if (selectedExerciseName != null) {
            val logsForExercise = state.myWorkouts.filter { it.exerciseName == selectedExerciseName }
            if (logsForExercise.isEmpty()) {
                selectedExerciseName = null
            } else {
                ExerciseProgressDialog(
                    exerciseName = selectedExerciseName!!,
                    logs = logsForExercise,
                    onDismiss = { selectedExerciseName = null },
                    onEditLog = { id, w, s, r -> viewModel.updateWorkout(id, w, s, r) },
                    onDeleteLog = { viewModel.deleteWorkout(it) }
                )
            }
        }

        if (showWeightDialog) {
            var weightStr by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showWeightDialog = false },
                title = { Text("Zapisz dzisiejszą wagę") },
                text = {
                    OutlinedTextField(
                        value = weightStr, onValueChange = { weightStr = it },
                        label = { Text("Waga (kg)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        val w = weightStr.replace(",", ".").toDoubleOrNull()
                        if (w != null) {
                            viewModel.logWeight(w)
                            showWeightDialog = false
                        }
                    }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676))) { Text("Zapisz", color = Color.White) }
                },
                dismissButton = { TextButton(onClick = { showWeightDialog = false }) { Text("Anuluj", color = Color.Gray) } }
            )
        }

    }
}

@Composable
fun ExerciseProgressDialog(
    exerciseName: String, 
    logs: List<ClientWorkoutDto>, 
    onDismiss: () -> Unit, 
    onEditLog: (Int, Double, Int, Int) -> Unit,
    onDeleteLog: (Int) -> Unit
) {
    val startLog = logs.firstOrNull()
    val endLog = logs.lastOrNull()
    
    val startWeight = startLog?.weight?.toFloat() ?: 0f
    val startSets = startLog?.sets ?: 0
    val startReps = startLog?.reps ?: 0
    val endWeight = endLog?.weight?.toFloat() ?: 0f
    val endSets = endLog?.sets ?: 0
    val endReps = endLog?.reps ?: 0
    val points = logs.map { it.weight.toFloat() }

    var logToEdit by remember { mutableStateOf<ClientWorkoutDto?>(null) }

    val isTimeBased = exerciseName.contains("Deska", ignoreCase = true) || exerciseName.contains("Plank", ignoreCase = true)
    val repsLabel = if (isTimeBased) "sek." else "powt."

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Progres: $exerciseName", fontSize = 20.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Początkowy wpis: $startWeight kg x $startReps $repsLabel (Seria $startSets)", fontSize = 14.sp)
                Text("Obecny wpis: $endWeight kg x $endReps $repsLabel (Seria $endSets)", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00E676))
                Spacer(modifier = Modifier.height(24.dp))
                
                Text("Wykres wzrostu siły", fontSize = 12.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(8.dp))

                Canvas(modifier = Modifier.fillMaxWidth().height(150.dp).padding(8.dp)) {
                    val w = size.width
                    val h = size.height
                    val maxW = (points.maxOrNull() ?: 0f) + 5f
                    val minW = ((points.minOrNull() ?: 0f) - 5f).coerceAtLeast(0f)
                    val range = if (maxW == minW) 10f else (maxW - minW)
                    val xStep = if (points.size <= 1) 0f else w / (points.size - 1)

                    val path = Path()
                    points.forEachIndexed { i, weight ->
                        val x = i * xStep
                        val y = h - ((weight - minW) / range) * h
                        if (i == 0) {
                            path.moveTo(x, y)
                        } else {
                            path.lineTo(x, y)
                        }
                        drawCircle(
                            color = Color(0xFF00E676),
                            radius = 6.dp.toPx(),
                            center = Offset(x, y)
                        )
                    }
                    drawPath(path = path, color = Color(0xFF00E676), style = Stroke(width = 3.dp.toPx()))
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text("Historia wpisów:", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 120.dp)) {
                    // Sortowanie po ID zapewni kolejność od najnowszych
                    items(logs.sortedByDescending { it.id }) { log ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("• ${formatDisplayDate(log.date)} - Seria ${log.sets}: ${log.weight} kg x ${log.reps} $repsLabel", fontSize = 13.sp, modifier = Modifier.weight(1f))
                            Row {
                                IconButton(onClick = { logToEdit = log }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Filled.Edit, contentDescription = "Edytuj", tint = Color(0xFF1E88E5), modifier = Modifier.size(20.dp))
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                IconButton(onClick = { onDeleteLog(log.id) }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Usuń", tint = Color.Red, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Zamknij", color = Color(0xFF4A6B5D))
            }
        }
    )

    if (logToEdit != null) {
        EditWorkoutDialog(
            log = logToEdit!!,
            onDismiss = { logToEdit = null },
            onSubmit = { w, s, r ->
                onEditLog(logToEdit!!.id, w, s, r)
                logToEdit = null
            }
        )
    }
}

@Composable
fun EditWorkoutDialog(
    log: ClientWorkoutDto,
    onDismiss: () -> Unit,
    onSubmit: (Double, Int, Int) -> Unit
) {
    var weightStr by remember { mutableStateOf(log.weight.toString()) }
    var setsStr by remember { mutableStateOf(log.sets.toString()) }
    var repsStr by remember { mutableStateOf(log.reps.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edytuj wynik") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = weightStr, onValueChange = { weightStr = it },
                    label = { Text("Ciężar (kg)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                OutlinedTextField(
                    value = setsStr, onValueChange = { setsStr = it },
                    label = { Text("Serie") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = repsStr, onValueChange = { repsStr = it },
                    label = { Text("Powtórzenia") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val w = weightStr.replace(",", ".").toDoubleOrNull()
                val s = setsStr.toIntOrNull()
                val r = repsStr.toIntOrNull()
                if (w != null && s != null && r != null) {
                    onSubmit(w, s, r)
                }
            }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5))) { Text("Zapisz", color = Color.White) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } }
    )
}

/**
 * Buduje plik PDF z raportem postępów (statystyki, tabela, wykres).
 */
private fun buildProgressPdfFile(
    context: Context,
    summary: ProgressSummaryResponse,
    workouts: List<ClientWorkoutDto>,
    weightLogs: List<ClientProgressLogDto>
): java.io.File {
    val document = PdfDocument()
    val writer = ProgressPdfWriter(document)

    writer.drawTitle("Raport postępów", "Podsumowanie Twoich postępów treningowych")

    writer.drawSection("Statystyki")
    writer.drawLine("Dni od pierwszego treningu: ${summary.daysSinceFirstTraining}")
    writer.drawLine("Okres: ${summary.dateRange}")
    writer.drawLine("Liczba zapisanych treningów: ${workouts.size}")
    writer.drawLine("Liczba pomiarów wagi: ${weightLogs.size}")
    writer.blank(12f)

    writer.drawSection("Progresja ćwiczeń")
    val exerciseRows = if (summary.progressList.isNotEmpty()) {
        summary.progressList.map { exercise ->
            listOf(
                exercise.exerciseName,
                "${exercise.startWeight} kg",
                "${exercise.endWeight} kg",
                if (exercise.endWeight > exercise.startWeight) "+${exercise.endWeight - exercise.startWeight} kg" else "0 kg"
            )
        }
    } else {
        workouts.groupBy { it.exerciseName }.map { (name, logs) ->
            val first = logs.first().weight
            val last = logs.last().weight
            listOf(
                name,
                "${first.toInt()} kg",
                "${last.toInt()} kg",
                if (last > first) "+${(last - first).toInt()} kg" else "0 kg"
            )
        }
    }

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

    if (workouts.isNotEmpty()) {
        writer.blank(16f)
        writer.drawSection("Historia treningów")
        writer.drawTable(
            headers = listOf("Data", "Ćwiczenie", "Serie", "Powt.", "kg"),
            rows = workouts.sortedBy { it.date }.map { workout ->
                listOf(
                    formatDisplayDate(workout.date),
                    workout.exerciseName,
                    workout.sets.toString(),
                    workout.reps.toString(),
                    workout.weight.toString()
                )
            }
        )
    }

    if (weightLogs.isNotEmpty()) {
        writer.blank(16f)
        writer.drawSection("Historia wagi")
        writer.drawTable(
            headers = listOf("Data", "Waga (kg)", "Notatka"),
            rows = weightLogs.map { log ->
                listOf(
                    formatDisplayDate(log.logDate),
                    log.weight.toString(),
                    log.notes.orEmpty().ifBlank { "-" }
                )
            }
        )
    }

    writer.drawFooter("Wygenerowano przez FitManager")
    writer.finish()

    val cacheFile = java.io.File(context.cacheDir, "progress-report.pdf")
    cacheFile.outputStream().use { outputStream ->
        document.writeTo(outputStream)
    }
    document.close()
    return cacheFile
}

private fun openPdfFile(context: Context, file: java.io.File) {
    val uri = FileProvider.getUriForFile(context, context.packageName + ".provider", file)
    val viewIntent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/pdf")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    val chooser = Intent.createChooser(viewIntent, "Otwórz raport PDF")
    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    try {
        context.startActivity(chooser)
        Toast.makeText(context, "Raport PDF wygenerowany.", Toast.LENGTH_SHORT).show()
    } catch (_: android.content.ActivityNotFoundException) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(Intent.createChooser(shareIntent, "Udostępnij raport PDF"))
            Toast.makeText(context, "Brak aplikacji PDF. Udostępnij plik ręcznie.", Toast.LENGTH_LONG).show()
        } catch (_: android.content.ActivityNotFoundException) {
            Toast.makeText(
                context,
                "Raport zapisany: ${file.absolutePath}",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}

private class ProgressPdfWriter(private val document: PdfDocument) {
    private var pageNumber = 0
    private lateinit var page: PdfDocument.Page
    private lateinit var canvas: android.graphics.Canvas
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
            canvas.drawText(
                value.toInt().toString(),
                left,
                top - 4f,
                bodyPaint
            )
            canvas.drawText(
                labels[index],
                left,
                chartBottom + 14f,
                bodyPaint
            )
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
        if (y + required > 780f) {
            newPage()
        }
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