package biali.fitmanager

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.sp
import android.widget.Toast
import biali.fitmanager.network.ApiResult
import biali.fitmanager.network.FitManagerRepository
import biali.fitmanager.network.SessionManager
import biali.fitmanager.pdf.ProgressReportGenerator
import biali.fitmanager.validation.InputValidator
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

private fun logsInSameSession(log: ClientWorkoutDto, allLogs: List<ClientWorkoutDto>): List<ClientWorkoutDto> =
    allLogs.filter {
        it.exerciseName == log.exerciseName &&
            ExerciseProgressMetrics.sessionGroupKey(it) == ExerciseProgressMetrics.sessionGroupKey(log)
    }.sortedBy { it.sets }

private fun availableSetNumbers(sessionLogs: List<ClientWorkoutDto>): List<Int> {
    if (sessionLogs.isEmpty()) return listOf(1)
    return (1..sessionLogs.size).toList()
}

class ProgressActivity : ComponentActivity() {
    private val viewModel: ProgressViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SessionManager.initialize(applicationContext)
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
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            when {
                state.isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFF00E676))
                    }
                }
                state.error != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                    ) {
                        Text(text = "Błąd: ${state.error}", color = MaterialTheme.colorScheme.error)
                    }
                }
                else -> {
                    val workouts = state.myWorkouts
                    val groupedWorkouts = workouts.groupBy { it.exerciseName }.toList()
                    val progressLogs = state.progressLogs
                    val latestLog = progressLogs.lastOrNull()

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        item {
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
                                            Text(
                                                "${latestLog.weight} kg",
                                                fontSize = 24.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF4A6B5D)
                                            )
                                            Text(
                                                "Ostatni pomiar: ${formatDisplayDate(latestLog.logDate)}",
                                                fontSize = 12.sp,
                                                color = Color.Gray
                                            )
                                        } else {
                                            Text(
                                                "Brak pomiarów",
                                                fontSize = 20.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.DarkGray
                                            )
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
                                    Text(
                                        text = "Notatka trenera:\n${latestLog.notes}",
                                        fontSize = 13.sp,
                                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                        modifier = Modifier.padding(16.dp)
                                    )
                                }
                            }
                        }

                        item { Spacer(modifier = Modifier.height(24.dp)) }

                        item {
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
                        }

                        item { Spacer(modifier = Modifier.height(24.dp)) }

                        item {
                            Text(
                                text = "Analiza wyników",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4A6B5D)
                            )
                            Text(
                                text = "Wykresy liczą objętość (kg × powt.) lub łączny czas trzymania",
                                fontSize = 12.sp,
                                color = Color.Gray,
                                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                            )
                        }

                        item {
                            Button(
                                onClick = {
                                    scope.launch {
                                        val repo = FitManagerRepository()
                                        when (val res = repo.getProgressSummary()) {
                                            is ApiResult.Success -> {
                                                try {
                                                    val pdfFile = withContext(Dispatchers.IO) {
                                                        val file = java.io.File(context.cacheDir, "progress-report.pdf")
                                                        ProgressReportGenerator.writeTo(
                                                            file,
                                                            ProgressReportMapper.from(
                                                                res.data,
                                                                state.myWorkouts,
                                                                state.progressLogs
                                                            )
                                                        )
                                                        file
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
                                                Toast.makeText(
                                                    context,
                                                    "Sesja wygasła. Zaloguj się ponownie.",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                            is ApiResult.Error -> {
                                                Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                },
                                enabled = workouts.isNotEmpty() || progressLogs.isNotEmpty(),
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A6B5D))
                            ) {
                                Text("Generuj raport postępów", color = Color.White)
                            }
                        }

                        item { Spacer(modifier = Modifier.height(16.dp)) }

                        items(groupedWorkouts, key = { it.first }) { (exerciseName, _) ->
                            val summary = ExerciseProgressMetrics.summaryForExercise(workouts, exerciseName)
                            val maxLabel = ExerciseProgressMetrics.maxRecordLabel(exerciseName, summary.maxWeightEver)
                            val volumeLabel = ExerciseProgressMetrics.formatVolumeLabel(exerciseName, summary.latestVolume)
                            val trend = summary.trendPercent

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                                    .clickable { selectedExerciseName = exerciseName },
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = exerciseName,
                                                color = Color(0xFF2E3D36),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp
                                            )
                                            if (maxLabel.isNotBlank()) {
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = maxLabel,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = Color(0xFF1E88E5)
                                                )
                                            }
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                text = volumeLabel,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF4A6B5D)
                                            )
                                            if (trend != null) {
                                                val trendColor = if (trend >= 0) Color(0xFF00C853) else Color(0xFFE53935)
                                                val sign = if (trend >= 0) "+" else ""
                                                Text(
                                                    text = "$sign${"%.0f".format(trend)}%",
                                                    fontSize = 11.sp,
                                                    color = trendColor,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        MiniProgressSparkline(
                                            values = summary.chartValues,
                                            isTimeBased = summary.isTimeBased,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                            contentDescription = "Szczegóły",
                                            tint = Color(0xFF00C853),
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                            }
                        }

                        item { Spacer(modifier = Modifier.height(32.dp)) }
                    }
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
                    onEditLog = { id, w, s, r, swapId -> viewModel.updateWorkout(id, w, s, r, swapId) },
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
    onEditLog: (Int, Double, Int, Int, Int?) -> Unit,
    onDeleteLog: (Int) -> Unit
) {
  val summary = ExerciseProgressMetrics.summaryForExercise(logs, exerciseName)
  val snapshots = summary.snapshots
  val startSnap = snapshots.firstOrNull()
  val endSnap = snapshots.lastOrNull()
  val chartValues = summary.chartValues

    var logToEdit by remember { mutableStateOf<ClientWorkoutDto?>(null) }

    val isTimeBased = summary.isTimeBased
    val repsLabel = if (isTimeBased) "sek." else "powt."

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Progres: $exerciseName", fontSize = 20.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Rekord", fontSize = 11.sp, color = Color.Gray)
                        Text(
                            ExerciseProgressMetrics.maxRecordLabel(exerciseName, summary.maxWeightEver),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color(0xFF1E88E5)
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Najlepsza objętość", fontSize = 11.sp, color = Color.Gray)
                        Text(
                            ExerciseProgressMetrics.formatVolumeLabel(exerciseName, summary.bestVolume),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color(0xFF00C853)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                if (startSnap != null && endSnap != null) {
                    Text(
                        "Pierwszy trening: ${ExercisePlanHelper.formatDisplayDate(startSnap.date)} — ${ExerciseProgressMetrics.formatVolumeLabel(exerciseName, startSnap.volumeScore)}",
                        fontSize = 13.sp,
                        color = Color.Gray
                    )
                    Text(
                        "Ostatni trening: ${ExercisePlanHelper.formatDisplayDate(endSnap.date)} — ${ExerciseProgressMetrics.formatVolumeLabel(exerciseName, endSnap.volumeScore)}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF4A6B5D)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))

                Text(ExerciseProgressMetrics.chartAxisLabel(exerciseName), fontSize = 12.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(8.dp))
                ProgressStrengthChart(values = chartValues, isTimeBased = isTimeBased)

                Spacer(modifier = Modifier.height(16.dp))
                Text("Historia treningów:", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))

                val sessionGroups = logs
                    .groupBy { ExerciseProgressMetrics.sessionGroupKey(it) }
                    .values
                    .sortedByDescending { ExercisePlanHelper.formatDisplayDate(it.first().date) }

                val dateCounts = sessionGroups.groupingBy {
                    ExercisePlanHelper.formatDisplayDate(it.first().date)
                }.eachCount()

                fun sessionTitle(sessionLogs: List<ClientWorkoutDto>): String {
                    val dateLabel = ExercisePlanHelper.formatDisplayDate(sessionLogs.first().date)
                    return if ((dateCounts[dateLabel] ?: 1) > 1) {
                        val sameDateGroups = sessionGroups.filter {
                            ExercisePlanHelper.formatDisplayDate(it.first().date) == dateLabel
                        }
                        val order = sameDateGroups.indexOf(sessionLogs) + 1
                        "Trening $order · $dateLabel"
                    } else {
                        "Trening · $dateLabel"
                    }
                }

                LazyColumn(
                    modifier = Modifier.heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    sessionGroups.forEach { sessionLogs ->
                        val sessionTitle = sessionTitle(sessionLogs)
                        val volumeSummary = ExerciseProgressMetrics.sessionVolumeLabel(exerciseName, sessionLogs)
                        val sortedSets = sessionLogs.sortedBy { it.sets }

                        item(key = "session_${ExerciseProgressMetrics.sessionGroupKey(sessionLogs.first())}") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFF3F7F5))
                                    .padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = sessionTitle,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = Color(0xFF4A6B5D)
                                        )
                                        Text(
                                            text = "${sortedSets.size} ${ExercisePlanHelper.polishSeries(sortedSets.size)}",
                                            fontSize = 11.sp,
                                            color = Color.Gray
                                        )
                                    }
                                    Surface(
                                        color = Color(0xFFE8F5E9),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = volumeSummary,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF2E7D32)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                HorizontalDivider(color = Color(0xFFDCE5E0))
                                Spacer(modifier = Modifier.height(6.dp))

                                sortedSets.forEach { log ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val setVol = if (isTimeBased) {
                                            "${log.reps} sek."
                                        } else if (log.weight > 0) {
                                            "${ExercisePlanHelper.formatWeight(log.weight)} kg × ${log.reps} = ${ExerciseProgressMetrics.formatVolumeNumber(log.weight * log.reps)}"
                                        } else {
                                            "${log.reps} $repsLabel"
                                        }
                                        Text(
                                            text = "Seria ${log.sets}: $setVol",
                                            fontSize = 12.sp,
                                            modifier = Modifier.weight(1f),
                                            color = Color(0xFF37474F)
                                        )
                                        Row {
                                            IconButton(
                                                onClick = { logToEdit = log },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    Icons.Filled.Edit,
                                                    contentDescription = "Edytuj",
                                                    tint = Color(0xFF1E88E5),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            IconButton(
                                                onClick = { onDeleteLog(log.id) },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    Icons.Filled.Delete,
                                                    contentDescription = "Usuń",
                                                    tint = Color.Red,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
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
            exerciseName = exerciseName,
            allExerciseLogs = logs,
            onDismiss = { logToEdit = null },
            onSubmit = { w, s, r, swapId ->
                onEditLog(logToEdit!!.id, w, s, r, swapId)
                logToEdit = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditWorkoutDialog(
    log: ClientWorkoutDto,
    exerciseName: String,
    allExerciseLogs: List<ClientWorkoutDto>,
    onDismiss: () -> Unit,
    onSubmit: (Double, Int, Int, swapWithId: Int?) -> Unit
) {
    val isTimeBased = ExercisePlanHelper.isTimeBased(exerciseName)
    val sessionLogs = logsInSameSession(log, allExerciseLogs)
    val setOptions = availableSetNumbers(sessionLogs)
    val initialSet = if (log.sets in setOptions) log.sets else setOptions.firstOrNull() ?: 1

    var weightStr by remember(log.id) { mutableStateOf(log.weight.toString()) }
    var selectedSet by remember(log.id) { mutableStateOf(initialSet) }
    var repsStr by remember(log.id) { mutableStateOf(log.reps.toString()) }
    var validationError by remember { mutableStateOf<String?>(null) }
    var setMenuExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edytuj wynik — $exerciseName") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Trening z ${formatDisplayDate(log.date)} · ${sessionLogs.size} ${ExercisePlanHelper.polishSeries(sessionLogs.size)}",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                if (selectedSet != log.sets) {
                    val swapTarget = sessionLogs.find { it.id != log.id && it.sets == selectedSet }
                    if (swapTarget != null) {
                        Text(
                            text = "Serie $selectedSet i ${log.sets} zostaną zamienione miejscami.",
                            fontSize = 11.sp,
                            color = Color(0xFF1E88E5)
                        )
                    }
                }

                ExposedDropdownMenuBox(
                    expanded = setMenuExpanded,
                    onExpandedChange = { setMenuExpanded = it }
                ) {
                    OutlinedTextField(
                        value = "Seria $selectedSet",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Numer serii") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = setMenuExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = setMenuExpanded,
                        onDismissRequest = { setMenuExpanded = false }
                    ) {
                        setOptions.forEach { setNum ->
                            DropdownMenuItem(
                                text = { Text("Seria $setNum") },
                                onClick = {
                                    selectedSet = setNum
                                    setMenuExpanded = false
                                    validationError = null
                                }
                            )
                        }
                    }
                }

                if (!isTimeBased) {
                    OutlinedTextField(
                        value = weightStr,
                        onValueChange = {
                            weightStr = it
                            validationError = null
                        },
                        label = { Text("Ciężar (kg)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        isError = validationError != null
                    )
                }

                OutlinedTextField(
                    value = repsStr,
                    onValueChange = {
                        repsStr = it
                        validationError = null
                    },
                    label = { Text(if (isTimeBased) "Czas (sek.)" else "Powtórzenia") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    isError = validationError != null
                )

                validationError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val w = if (isTimeBased) 0.0 else weightStr.replace(",", ".").toDoubleOrNull()
                    val r = repsStr.toIntOrNull()
                    if (!isTimeBased) {
                        val weightValidationError = InputValidator.validateWeight(w)
                        if (weightValidationError != null) {
                            validationError = weightValidationError
                            return@Button
                        }
                    }
                    if (r == null || r <= 0) {
                        validationError = if (isTimeBased) "Podaj poprawny czas w sekundach." else "Podaj poprawną liczbę powtórzeń."
                        return@Button
                    }
                    val swapTarget = if (selectedSet != log.sets) {
                        sessionLogs.find { it.id != log.id && it.sets == selectedSet }
                    } else {
                        null
                    }
                    onSubmit(w ?: 0.0, selectedSet, r, swapTarget?.id)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5))
            ) { Text("Zapisz", color = Color.White) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } }
    )
}
