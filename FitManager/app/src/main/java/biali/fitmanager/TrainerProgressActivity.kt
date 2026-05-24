package biali.fitmanager

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import biali.fitmanager.network.SessionManager
import biali.fitmanager.ui.theme.Green80
import biali.fitmanager.network.UserResponse
import biali.fitmanager.ui.theme.GymManagerTheme
import biali.fitmanager.ui.theme.LightGreen80

// TODO: Docelowo przenieść te modele do osobnego pliku (np. w paczce network) 
// i pobierać je z backendu (repozytorium).
data class ClientProgressLog(
    val id: Int,
    val clientName: String,
    val logDate: String,
    val weight: Double,
    val notes: String?
)

data class CreateProgressRequest(val clientId: Int, val weight: Double, val notes: String)
data class CreateSessionRequest(val title: String, val startTime: String, val durationMinutes: Int)

data class ClientExercise(val id: Int, val name: String, val bodyPart: String)
data class ClientSessionExercise(val id: Int, val sessionId: Int, val exerciseName: String, val sets: Int, val reps: Int, val weight: Double)
data class AddSessionExerciseRequest(val exerciseId: Int, val sets: Int, val reps: Int, val weight: Double)

data class ClientTrainingSession(
    val id: Int,
    val title: String,
    val date: String,
    val duration: String
)

class TrainerProgressActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val viewModel = ViewModelProvider(this)[TrainerProgressViewModel::class.java]

        setContent {
            GymManagerTheme {
                val state by viewModel.state.collectAsState()

                LaunchedEffect(Unit) {
                    viewModel.fetchData()
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = { ProgressTopBar(onLogout = ::logout) },
                    bottomBar = { ProgressBottomNav(onNavigateToClients = ::navigateToClients) }
                ) { innerPadding ->
                    ProgressContent(
                        modifier = Modifier.padding(innerPadding),
                        state = state,
                        viewModel = viewModel
                    )
                }
            }
        }
    }

    private fun navigateToClients() {
        val intent = Intent(this, TrainerUsersActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        }
        startActivity(intent)
    }

    private fun logout() {
        SessionManager.clearSession()
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressTopBar(onLogout: () -> Unit) {
    TopAppBar(
        title = {
            Text("Zarządzanie postępem", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        },
        actions = {
            TextButton(onClick = onLogout) {
                Text("Wyloguj")
            }
        }
    )
}

@Composable
fun ProgressContent(
    modifier: Modifier = Modifier,
    state: TrainerProgressUiState,
    viewModel: TrainerProgressViewModel
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Postępy, 1 = Treningi

    // Grupowanie pomiarów po imieniu klienta
    val logsByClient = state.progressLogs.groupBy { it.clientName }
    var showProgressDialog by remember { mutableStateOf(false) }
    var showSessionDialog by remember { mutableStateOf(false) }
    var selectedSessionForExercise by remember { mutableStateOf<ClientTrainingSession?>(null) }
    var selectedChartExercise by remember { mutableStateOf<String?>(null) }
    var showClientWorkoutsFor by remember { mutableStateOf<String?>(null) }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        // Statystyki ogólne (Header)
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatBox("Podopieczni", logsByClient.keys.size.toString())
            StatBox("Treningi", state.trainingSessions.size.toString())
            StatBox("Pomiary", state.progressLogs.size.toString())
        }

        TabRow(selectedTabIndex = selectedTab, containerColor = Color.Transparent) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Pomiary / Postęp", fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Treningi", fontWeight = FontWeight.Bold) }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (selectedTab == 0) {
            if (state.isLoading && state.progressLogs.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            }
            state.error?.let { Text(text = it, color = Color.Red, modifier = Modifier.padding(8.dp)) }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(logsByClient.entries.toList()) { (clientName, logs) ->
                val clientWorkoutLogs = state.clientWorkouts.filter { it.clientName == clientName }
                ClientProgressDashboardCard(clientName = clientName, logs = logs, onShowWorkouts = { showClientWorkoutsFor = clientName }, workoutCount = clientWorkoutLogs.size)
                }
                item {
                    Button(
                        onClick = { showProgressDialog = true },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5)) // Niebieski akcent
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Dodaj", modifier = Modifier.padding(end = 8.dp))
                        Text("Dodaj nowy pomiar", color = Color.White)
                    }
                }
            }
        } else {
            if (state.isLoading && state.trainingSessions.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            }
            state.error?.let { Text(text = it, color = Color.Red, modifier = Modifier.padding(8.dp)) }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(state.trainingSessions) { session ->
                    val sessionExercises = state.sessionExercises.filter { it.sessionId == session.id }
                    TimelineSessionCard(
                        session = session,
                        exercises = sessionExercises,
                        onAddExercise = { selectedSessionForExercise = session },
                        onDeleteExercise = { viewModel.deleteSessionExercise(it) },
                        onShowExerciseChart = { selectedChartExercise = it }
                    )
                }
                item {
                    Button(
                        onClick = { showSessionDialog = true },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5))
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Dodaj", modifier = Modifier.padding(end = 8.dp))
                        Text("Zaplanuj nowy trening", color = Color.White)
                    }
                }
            }
        }

        if (showProgressDialog) {
            AddProgressDialog(
                clients = state.clients,
                onDismiss = { showProgressDialog = false },
                onSubmit = { clientId, weight, notes ->
                    viewModel.addProgress(clientId, weight, notes)
                    showProgressDialog = false
                }
            )
        }

        if (showSessionDialog) {
            AddSessionDialog(
                onDismiss = { showSessionDialog = false },
                onSubmit = { title, startTime, duration ->
                    viewModel.addSession(title, startTime, duration)
                    showSessionDialog = false
                }
            )
        }

        if (selectedSessionForExercise != null) {
            AddSessionExerciseDialog(
                exercises = state.availableExercises,
                onDismiss = { selectedSessionForExercise = null },
                onSubmit = { exerciseId, sets, reps, weight ->
                    viewModel.addSessionExercise(selectedSessionForExercise!!.id, exerciseId, sets, reps, weight)
                    selectedSessionForExercise = null
                }
            )
        }

        if (selectedChartExercise != null) {
            TrainerExerciseChartDialog(
                exerciseName = selectedChartExercise!!,
                sessionExercises = state.sessionExercises,
                trainingSessions = state.trainingSessions,
                onDismiss = { selectedChartExercise = null }
            )
        }

        if (showClientWorkoutsFor != null) {
            ClientRealWorkoutsDialog(
                clientName = showClientWorkoutsFor!!,
                workouts = state.clientWorkouts.filter { it.clientName == showClientWorkoutsFor },
                onDismiss = { showClientWorkoutsFor = null }
            )
        }
    }
}

@Composable
fun StatBox(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Green80)
        Text(text = label, fontSize = 12.sp, color = Color.Gray)
    }
}

@Composable
fun ClientProgressDashboardCard(clientName: String, logs: List<ClientProgressLog>, onShowWorkouts: () -> Unit, workoutCount: Int) {
    // Zakładamy, że logi przychodzą posortowane (lub sortujemy je tutaj) - dla wykresu odwracamy, by najstarsze były po lewej
    val chronologicalLogs = logs.reversed()
    val latestLog = logs.firstOrNull()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(40.dp).clip(CircleShape).background(LightGreen80),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = clientName.take(1), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Green80)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(text = clientName, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(text = "Ostatni pomiar: ${latestLog?.logDate ?: "Brak"}", fontSize = 12.sp, color = Color.Gray)
                    }
                }
                if (latestLog != null) {
                    Text(text = "${latestLog.weight} kg", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = Green80)
                }
            }

            if (chronologicalLogs.size > 1) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = "Progresja wagi", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.SemiBold)
                WeightLineChart(logs = chronologicalLogs)
            }

            if (latestLog?.notes != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Box(modifier = Modifier.fillMaxWidth().background(Color(0xFFF5F5F5), RoundedCornerShape(8.dp)).padding(12.dp)) {
                    Text(text = "Notatka trenera:\n${latestLog.notes}", fontSize = 13.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onShowWorkouts, modifier = Modifier.fillMaxWidth()) {
                Text(text = "Samodzielne wpisy klienta ($workoutCount)", color = Color(0xFF1E88E5))
            }
        }
    }
}

@Composable
fun WeightLineChart(logs: List<ClientProgressLog>) {
    val weights = logs.map { it.weight.toFloat() }
    val maxWeight = (weights.maxOrNull() ?: 100f) + 2f
    val minWeight = (weights.minOrNull() ?: 0f) - 2f
    val range = if (maxWeight == minWeight) 10f else (maxWeight - minWeight)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .padding(vertical = 12.dp, horizontal = 8.dp)
    ) {
        val width = size.width
        val height = size.height
        val xStep = if (weights.size > 1) width / (weights.size - 1) else width

        val path = Path()
        weights.forEachIndexed { index, weight ->
            val x = index * xStep
            val y = height - ((weight - minWeight) / range) * height

            if (index == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
            drawCircle(
                color = Color(0xFF1E88E5), // Niebieski punkt
                radius = 5.dp.toPx(),
                center = Offset(x, y)
            )
        }

        drawPath(
            path = path,
            color = Color(0xFF1E88E5),
            style = Stroke(width = 3.dp.toPx())
        )
    }
}

@Composable
fun TimelineSessionCard(
    session: ClientTrainingSession,
    exercises: List<ClientSessionExercise>,
    onAddExercise: () -> Unit,
    onDeleteExercise: (Int) -> Unit,
    onShowExerciseChart: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.DateRange, contentDescription = "Kalendarz", tint = Green80, modifier = Modifier.size(32.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = session.title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.DarkGray)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "Termin: ${session.date}", fontSize = 14.sp, color = Color.Gray)
                    Text(text = "Czas trwania: ${session.duration}", fontSize = 14.sp, color = Color.Gray)
                }
                IconButton(onClick = onAddExercise) {
                    Icon(Icons.Filled.Add, contentDescription = "Dodaj ćwiczenie", tint = Color(0xFF1E88E5))
                }
            }
            if (exercises.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color(0xFFE0E0E0))
                exercises.forEach { ex ->
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { onShowExerciseChart(ex.exerciseName) }
                            .padding(vertical = 8.dp, horizontal = 4.dp), 
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text(text = ex.exerciseName, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            val isTimeBased = ex.exerciseName.contains("Deska", ignoreCase = true) || ex.exerciseName.contains("Plank", ignoreCase = true)
                            val repsLabel = if (isTimeBased) "sek." else "powt."
                            val weightLabel = if (ex.weight > 0.0) " | ${ex.weight} kg" else ""
                            Text(text = "${ex.sets} serie x ${ex.reps} $repsLabel$weightLabel", fontSize = 12.sp, color = Color.Gray)
                        }
                        IconButton(onClick = { onDeleteExercise(ex.id) }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Filled.Delete, contentDescription = "Usuń", tint = Color.Red)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddProgressDialog(
    clients: List<UserResponse>,
    onDismiss: () -> Unit,
    onSubmit: (clientId: Int, weight: Double, notes: String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var selectedClient by remember { mutableStateOf<UserResponse?>(clients.firstOrNull()) }
    var weightStr by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Dodaj pomiar") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = selectedClient?.let { "${it.firstName} ${it.lastName}" } ?: "Brak klientów",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Podopieczny") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        clients.forEach { client ->
                            DropdownMenuItem(
                                text = { Text("${client.firstName} ${client.lastName}") },
                                onClick = {
                                    selectedClient = client
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = weightStr,
                    onValueChange = { weightStr = it },
                    label = { Text("Waga (kg)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notatki") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                // Zastępujemy przecinek kropką i konwertujemy
                val w = weightStr.replace(",", ".").toDoubleOrNull()
                if (selectedClient != null && w != null) {
                    onSubmit(selectedClient!!.id, w, notes)
                }
            }) { Text("Dodaj") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } }
    )
}

@Composable
fun AddSessionDialog(
    onDismiss: () -> Unit,
    onSubmit: (title: String, startTime: String, duration: Int) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var startTime by remember { mutableStateOf("") }
    var durationStr by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Zaplanuj trening") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Nazwa treningu") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = startTime,
                    onValueChange = { startTime = it },
                    label = { Text("Data i czas") },
                    placeholder = { Text("np. 2024-05-20 18:00") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = durationStr,
                    onValueChange = { durationStr = it },
                    label = { Text("Czas trwania (min)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val d = durationStr.toIntOrNull()
                if (title.isNotBlank() && startTime.isNotBlank() && d != null) {
                    // Baza PostgreSQL oczekuje sekund, dodajemy :00 jeżeli użytkownik wpisał np. tylko 18:00
                    val formattedTime = if (startTime.count { it == ':' } == 1) "$startTime:00" else startTime
                    onSubmit(title, formattedTime, d)
                }
            }) { Text("Zapisz") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSessionExerciseDialog(
    exercises: List<ClientExercise>,
    onDismiss: () -> Unit,
    onSubmit: (exerciseId: Int, sets: Int, reps: Int, weight: Double) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var selectedExercise by remember { mutableStateOf<ClientExercise?>(exercises.firstOrNull()) }
    var setsStr by remember { mutableStateOf("") }
    var repsStr by remember { mutableStateOf("") }
    var weightStr by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Dodaj ćwiczenie") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = selectedExercise?.name ?: "Wybierz ćwiczenie",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Ćwiczenie") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        exercises.forEach { exercise ->
                            DropdownMenuItem(
                                text = { Text("${exercise.name} (${exercise.bodyPart})") },
                                onClick = {
                                    selectedExercise = exercise
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = setsStr,
                        onValueChange = { setsStr = it },
                        label = { Text("Serie") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    val isTimeBased = selectedExercise?.name?.contains("Deska", ignoreCase = true) == true || selectedExercise?.name?.contains("Plank", ignoreCase = true) == true
                    OutlinedTextField(
                        value = repsStr,
                        onValueChange = { repsStr = it },
                        label = { Text(if (isTimeBased) "Sekundy" else "Powt.") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = weightStr,
                    onValueChange = { weightStr = it },
                    label = { Text("Ciężar (kg)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val s = setsStr.toIntOrNull()
                val r = repsStr.toIntOrNull()
                val w = weightStr.replace(",", ".").toDoubleOrNull()
                if (selectedExercise != null && s != null && r != null && w != null) {
                    onSubmit(selectedExercise!!.id, s, r, w)
                }
            }) { Text("Dodaj") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } }
    )
}

@Composable
fun TrainerExerciseChartDialog(
    exerciseName: String,
    sessionExercises: List<ClientSessionExercise>,
    trainingSessions: List<ClientTrainingSession>,
    onDismiss: () -> Unit
) {
    // Znajduje wszystkie wyniki tego ćwiczenia i paruje je z datą z sesji
    val dataPoints = sessionExercises
        .filter { it.exerciseName == exerciseName }
        .mapNotNull { ex ->
            val session = trainingSessions.find { it.id == ex.sessionId }
            if (session != null) session.date to ex.weight.toFloat() else null
        }
        .sortedBy { it.first } // Uproszczone sortowanie po dacie/id

    val weights = dataPoints.map { it.second }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Historia: $exerciseName", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                if (weights.isEmpty()) {
                    Text("Brak danych do wyświetlenia.", color = Color.Gray)
                } else if (weights.size < 2) {
                    Text("Za mało danych, aby narysować wykres (wymagane min. 2 treningi).", color = Color.Gray)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Twój jedyny wynik: ${weights.first()} kg", fontWeight = FontWeight.SemiBold, color = Color(0xFF1E88E5))
                } else {
                    Text("Progresja obciążenia na przestrzeni treningów:", fontSize = 14.sp, color = Color.DarkGray)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Możemy tu wykorzystać ten sam wykres, co przy wadze ciała
                    WeightLineChart(logs = weights.map { ClientProgressLog(0, "", "", it.toDouble(), null) })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Zamknij", color = Color(0xFF1E88E5))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientRealWorkoutsDialog(
    clientName: String,
    workouts: List<ClientWorkoutDto>,
    onDismiss: () -> Unit
) {
    // Grupujemy treningi po nazwie ćwiczenia
    var selectedExercise by remember { mutableStateOf<String?>(workouts.firstOrNull()?.exerciseName) }
    val uniqueExercises = workouts.map { it.exerciseName }.distinct()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Wyniki wpisane przez: $clientName", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (workouts.isEmpty()) {
                    Text("Klient nie zapisał jeszcze żadnego wyniku.")
                } else {
                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                        OutlinedTextField(
                            value = selectedExercise ?: "", onValueChange = {}, readOnly = true,
                            label = { Text("Wybierz ćwiczenie") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            uniqueExercises.forEach { ex ->
                                DropdownMenuItem(text = { Text(ex) }, onClick = { selectedExercise = ex; expanded = false })
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    val exerciseLogs = workouts.filter { it.exerciseName == selectedExercise }.sortedBy { it.date }
                    Text("Historia wykonań:", fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                        items(exerciseLogs) { log ->
                            Text("• ${log.date}: ${log.weight}kg x ${log.reps} powt.", fontSize = 14.sp)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Zamknij") } }
    )
}

@Composable
private fun ProgressBottomNav(onNavigateToClients: () -> Unit) {
    NavigationBar(
        containerColor = Color.White,
        tonalElevation = 8.dp
    ) {
        NavigationBarItem(
            selected = false,
            onClick = { /* Brak akcji */ },
            label = { Text("Panel") },
            icon = { Icon(Icons.Filled.Home, contentDescription = "Panel") }
        )
        NavigationBarItem(
            selected = false,
            onClick = onNavigateToClients,
            label = { Text("Trener") },
            icon = { Icon(Icons.Filled.Person, contentDescription = "Trener") }
        )
        NavigationBarItem(
            selected = true,
            onClick = { /* Już tu jesteśmy */ },
            label = { Text("Postęp") },
            icon = { Icon(Icons.Filled.Edit, contentDescription = "Postęp") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Green80,
                selectedTextColor = Green80,
                indicatorColor = LightGreen80
            )
        )
        NavigationBarItem(
            selected = false,
            onClick = { },
            label = { Text("Konto") },
            icon = { Icon(Icons.Filled.Person, contentDescription = "Konto") }
        )
    }
}