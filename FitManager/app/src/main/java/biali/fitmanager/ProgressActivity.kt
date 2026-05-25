package biali.fitmanager

import android.os.Bundle
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class LogWorkoutRequest(val exerciseId: Int, val weight: Double, val sets: Int, val reps: Int, val sessionId: Int? = null)
data class ClientWorkoutDto(val id: Int, val clientName: String, val exerciseName: String, val weight: Double, val sets: Int, val reps: Int, val date: String, val sessionId: Int? = null)

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
                    ProgressScreen(viewModel, onBackClick = { finish() })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(viewModel: ProgressViewModel, onBackClick: () -> Unit) {

    val state by viewModel.state.collectAsState()

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
                                Text("Ostatni pomiar: ${latestLog.logDate}", fontSize = 12.sp, color = Color.Gray)
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
                            Text("• ${log.date} - Seria ${log.sets}: ${log.weight} kg x ${log.reps} $repsLabel", fontSize = 13.sp, modifier = Modifier.weight(1f))
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