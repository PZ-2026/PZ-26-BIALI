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
import biali.fitmanager.network.ProgressSummaryResponse
import biali.fitmanager.validation.InputValidator
import kotlinx.coroutines.launch

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
                                    generateAndOpenProgressPdf(context, res.data)
                                }
                                is ApiResult.Unauthorized -> {
                                    Toast.makeText(context, "Brak uprawnień.", Toast.LENGTH_SHORT).show()
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
            var weightError by remember { mutableStateOf<String?>(null) }
            AlertDialog(
                onDismissRequest = { showWeightDialog = false },
                title = { Text("Zapisz dzisiejszą wagę") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = weightStr,
                            onValueChange = {
                                weightStr = it
                                weightError = null
                            },
                            label = { Text("Waga (kg)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth(),
                            isError = weightError != null,
                            supportingText = weightError?.let { msg -> { Text(msg, color = MaterialTheme.colorScheme.error) } }
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        val w = weightStr.replace(",", ".").toDoubleOrNull()
                        val validationError = InputValidator.validateWeight(w)
                        if (validationError != null) {
                            weightError = validationError
                            return@Button
                        }
                        viewModel.logWeight(w!!)
                        showWeightDialog = false
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
                    val xStep = w / (points.size - 1)

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
    var validationError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edytuj wynik") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = weightStr, onValueChange = {
                        weightStr = it
                        validationError = null
                    },
                    label = { Text("Ciężar (kg)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = validationError != null
                )
                OutlinedTextField(
                    value = setsStr, onValueChange = { setsStr = it },
                    label = { Text("Serie") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = repsStr, onValueChange = { repsStr = it },
                    label = { Text("Powtórzenia") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                validationError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
            }
        },
        confirmButton = {
            Button(onClick = {
                val w = weightStr.replace(",", ".").toDoubleOrNull()
                val s = setsStr.toIntOrNull()
                val r = repsStr.toIntOrNull()
                val weightValidationError = InputValidator.validateWeight(w)
                if (weightValidationError != null) {
                    validationError = weightValidationError
                    return@Button
                }
                if (w != null && s != null && r != null) {
                    onSubmit(w, s, r)
                } else {
                    validationError = "Podaj poprawne wartości serii i powtórzeń."
                }
            }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5))) { Text("Zapisz", color = Color.White) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } }
    )
}

/**
 * Generuje PDF z raportem postępów, zapisuje do cache i otwiera natychmiast.
 * Używa android.graphics.pdf.PdfDocument (tak samo jak generateSessionPdf w HomeActivity).
 */
private fun generateAndOpenProgressPdf(context: Context, summary: ProgressSummaryResponse) {
    try {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4
        val page = document.startPage(pageInfo)
        val canvas = page.canvas
        val paint = Paint()

        // Białe tło
        paint.color = android.graphics.Color.WHITE
        canvas.drawRect(0f, 0f, 595f, 842f, paint)

        paint.color = android.graphics.Color.BLACK

        // Tytuł
        paint.textSize = 22f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("Raport postępów", 50f, 60f, paint)

        // Podtytuł
        paint.textSize = 14f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("Podsumowanie Twoich postępów treningowych", 50f, 90f, paint)

        var y = 130f

        // Statystyki
        paint.textSize = 16f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("Statystyki", 50f, y, paint)
        y += 30f

        paint.textSize = 14f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("Dni od pierwszego treningu: ${summary.daysSinceFirstTraining}", 50f, y, paint)
        y += 25f
        canvas.drawText("Okres: ${summary.dateRange}", 50f, y, paint)
        y += 40f

        // Progresja ćwiczeń
        paint.textSize = 16f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("Progresja ćwiczeń", 50f, y, paint)
        y += 30f

        paint.textSize = 14f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        if (summary.progressList.isEmpty()) {
            canvas.drawText("Brak danych o progresji ćwiczeń.", 50f, y, paint)
            y += 25f
        } else {
            for (exercise in summary.progressList) {
                val arrow = if (exercise.endWeight > exercise.startWeight) "→" else "→"
                val text = "${exercise.exerciseName}: ${exercise.startWeight} kg $arrow ${exercise.endWeight} kg"
                canvas.drawText(text, 50f, y, paint)
                y += 25f

                // Jeśli brakuje miejsca, zakończ stronę
                if (y > 780f) {
                    document.finishPage(page)
                    // Nowa strona
                    val pageInfo2 = PdfDocument.PageInfo.Builder(595, 842, 2).create()
                    val page2 = document.startPage(pageInfo2)
                    val canvas2 = page2.canvas
                    paint.color = android.graphics.Color.WHITE
                    canvas2.drawRect(0f, 0f, 595f, 842f, paint)
                    paint.color = android.graphics.Color.BLACK
                    paint.textSize = 14f
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    y = 50f
                    // Kontynuuj na nowej stronie
                    canvas2.drawText(text, 50f, y, paint)
                    y += 25f
                    // Używamy canvas2 od teraz
                    canvas2.drawText("(ciąg dalszy)", 50f, y, paint)
                    y += 25f
                }
            }
        }

        y += 20f

        // Wykres słupkowy (tekstowo)
        if (summary.chartData.isNotEmpty()) {
            paint.textSize = 16f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("Ogólny postęp (wykres słupkowy)", 50f, y, paint)
            y += 30f

            paint.textSize = 14f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            val maxVal = summary.chartData.maxOrNull()?.toFloat() ?: 1f
            val barMaxWidth = 300f
            val barHeight = 20f
            val startX = 50f

            for ((index, value) in summary.chartData.withIndex()) {
                val barWidth = (value.toFloat() / maxVal) * barMaxWidth
                val label = when (index) {
                    0 -> "Początek"
                    summary.chartData.size - 1 -> "Koniec"
                    else -> "Etap $index"
                }

                // Rysuj etykietę
                canvas.drawText("$label:", startX, y, paint)
                y += 5f

                // Rysuj słupek
                paint.color = android.graphics.Color.rgb(0x4A, 0x6B, 0x5D)
                canvas.drawRect(startX, y, startX + barWidth, y + barHeight, paint)
                paint.color = android.graphics.Color.BLACK

                // Wartość nad słupkiem
                paint.textSize = 12f
                canvas.drawText("$value", startX + barWidth + 8f, y + barHeight - 2f, paint)
                paint.textSize = 14f

                y += barHeight + 20f

                // Jeśli brakuje miejsca, zakończ stronę
                if (y > 780f) {
                    document.finishPage(page)
                    val pageInfo3 = PdfDocument.PageInfo.Builder(595, 842, 3).create()
                    val page3 = document.startPage(pageInfo3)
                    val canvas3 = page3.canvas
                    paint.color = android.graphics.Color.WHITE
                    canvas3.drawRect(0f, 0f, 595f, 842f, paint)
                    paint.color = android.graphics.Color.BLACK
                    paint.textSize = 14f
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    y = 50f
                    canvas3.drawText("(ciąg dalszy wykresu)", 50f, y, paint)
                    y += 30f
                }
            }
        }

        // Stopka
        paint.textSize = 10f
        paint.color = android.graphics.Color.GRAY
        canvas.drawText("Wygenerowano przez FitManager", 50f, 820f, paint)

        document.finishPage(page)

        // Zapisz do pliku w cache
        val cacheFile = java.io.File(context.cacheDir, "progress-report.pdf")
        cacheFile.outputStream().use { outputStream ->
            document.writeTo(outputStream)
        }
        document.close()

        // Otwórz PDF natychmiast przez Intent
        val uri = FileProvider.getUriForFile(context, context.packageName + ".provider", cacheFile)
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(uri, "application/pdf")
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Błąd podczas generowania PDF: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}