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
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

data class CreateSessionRequest(val clientId: Int, val title: String, val startTime: String, val durationMinutes: Int)

data class ClientExercise(val id: Int, val name: String, val bodyPart: String)
data class ClientSessionExercise(val id: Int, val sessionId: Int, val exerciseName: String, val sets: Int, val reps: Int, val weight: Double)
data class AddSessionExerciseRequest(val exerciseId: Int, val sets: Int, val reps: Int, val weight: Double)

data class ClientTrainingSession(
    val id: Int,
    val title: String,
    val date: String,
    val duration: String,
    val clientName: String?,
    val status: String?
)

private fun getDaysAgoText(dateString: String): String {
    return try {
        val sdf = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault())
        val date = sdf.parse(dateString) ?: return ""
        val today = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        val logDate = date.time
        val days = java.util.concurrent.TimeUnit.MILLISECONDS.toDays(today - logDate)
        when {
            days == 0L -> "(dzisiaj)"
            days == 1L -> "(wczoraj)"
            days > 1L -> "($days dni temu)"
            else -> ""
        }
    } catch (e: Exception) { "" }
}

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
                    bottomBar = { ProgressBottomNav(onNavigateToHome = ::navigateToHome, onNavigateToClients = ::navigateToClients, onNavigateToAccount = ::navigateToAccount) }
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

    private fun navigateToHome() {
        val intent = Intent(this, TrainerHomeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        }
        startActivity(intent)
    }

    private fun navigateToClients() {
        val intent = Intent(this, TrainerUsersActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        }
        startActivity(intent)
    }

    private fun navigateToAccount() {
        val intent = Intent(this, AccountActivity::class.java).apply {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressContent(
    modifier: Modifier = Modifier,
    state: TrainerProgressUiState,
    viewModel: TrainerProgressViewModel
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Postępy, 1 = Treningi
    var selectedSubTab by remember { mutableIntStateOf(0) } // Podzakładki treningów

    var expandedFilter by remember { mutableStateOf(false) }
    var selectedFilterName by remember { mutableStateOf<String?>(null) }
    val uniqueClients = state.trainingSessions.mapNotNull { it.clientName }.distinct().sorted()

    // Grupowanie pomiarów po imieniu klienta
    val logsByClient = state.progressLogs.groupBy { it.clientName }
    var showEditNoteDialog by remember { mutableStateOf<Pair<Int, String>?>(null) }
    var showSessionDialog by remember { mutableStateOf(false) }
    var selectedSessionForExercise by remember { mutableStateOf<ClientTrainingSession?>(null) }
    var selectedChartExercise by remember { mutableStateOf<String?>(null) }
    var selectedSessionForExecution by remember { mutableStateOf<ClientTrainingSession?>(null) }
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
                    ClientProgressDashboardCard(
                        clientName = clientName, 
                        logs = logs,
                        onShowWorkouts = { showClientWorkoutsFor = clientName },
                        onEditNote = { logId, currentNote -> showEditNoteDialog = logId to currentNote }
                    )
                }
            }
        } else {
            TabRow(
                selectedTabIndex = selectedSubTab,
                containerColor = Color.Transparent,
                divider = {}
            ) {
                Tab(
                    selected = selectedSubTab == 0,
                    onClick = { selectedSubTab = 0 },
                    text = { Text("Szkice", fontSize = 13.sp, fontWeight = if (selectedSubTab == 0) FontWeight.Bold else FontWeight.Normal) }
                )
                Tab(
                    selected = selectedSubTab == 1,
                    onClick = { selectedSubTab = 1 },
                    text = { Text("Oczekujące", fontSize = 13.sp, fontWeight = if (selectedSubTab == 1) FontWeight.Bold else FontWeight.Normal) }
                )
                Tab(
                    selected = selectedSubTab == 2,
                    onClick = { selectedSubTab = 2 },
                    text = { Text("Zakończone", fontSize = 13.sp, fontWeight = if (selectedSubTab == 2) FontWeight.Bold else FontWeight.Normal) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (uniqueClients.isNotEmpty()) {
                ExposedDropdownMenuBox(
                    expanded = expandedFilter,
                    onExpandedChange = { expandedFilter = !expandedFilter }
                ) {
                    OutlinedTextField(
                        value = selectedFilterName ?: "Wszyscy klienci",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Filtruj po kliencie") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedFilter) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expandedFilter,
                        onDismissRequest = { expandedFilter = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Wszyscy klienci") },
                            onClick = { selectedFilterName = null; expandedFilter = false }
                        )
                        uniqueClients.forEach { clientName ->
                            DropdownMenuItem(
                                text = { Text(clientName) },
                                onClick = { selectedFilterName = clientName; expandedFilter = false }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (state.isLoading && state.trainingSessions.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            }
            state.error?.let { Text(text = it, color = Color.Red, modifier = Modifier.padding(8.dp)) }

            val filteredSessions = when (selectedSubTab) {
                0 -> state.trainingSessions.filter { it.status == "DRAFT" }
                1 -> state.trainingSessions.filter { it.status == "CONFIRMED" || (it.status != "DRAFT" && it.status != "COMPLETED") }
                else -> state.trainingSessions.filter { it.status == "COMPLETED" }
            }.filter { selectedFilterName == null || it.clientName == selectedFilterName }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (filteredSessions.isEmpty()) {
                    item {
                        Text(
                            text = "Brak treningów w tej kategorii.",
                            color = Color.Gray,
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    items(filteredSessions) { session ->
                        val sessionExercises = state.sessionExercises.filter { it.sessionId == session.id }
                        TimelineSessionCard(
                            session = session,
                            exercises = sessionExercises,
                            onAddExercise = { selectedSessionForExercise = session },
                            onDeleteExercise = { viewModel.deleteSessionExercise(it) },
                            onShowExerciseChart = { selectedChartExercise = it },
                            onShowSessionExecution = { selectedSessionForExecution = session },
                            onSendToClient = { viewModel.sendSessionToClient(session.id) },
                            onDeleteSession = { viewModel.deleteSession(session.id) }
                        )
                    }
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

        if (showEditNoteDialog != null) {
            EditNoteDialog(
                initialNote = showEditNoteDialog!!.second,
                onDismiss = { showEditNoteDialog = null },
                onSubmit = { note -> 
                    viewModel.updateProgressNote(showEditNoteDialog!!.first, note)
                    showEditNoteDialog = null
                }
            )
        }

        if (showSessionDialog) {
            AddSessionDialog(
                clients = state.clients,
                onDismiss = { showSessionDialog = false },
                onSubmit = { clientId, title, startTime, duration ->
                    viewModel.addSession(clientId, title, startTime, duration)
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

        if (selectedSessionForExecution != null) {
            SessionExecutionDialog(
                session = selectedSessionForExecution!!,
                workouts = state.clientWorkouts.filter { it.sessionId == selectedSessionForExecution!!.id },
                onDismiss = { selectedSessionForExecution = null }
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
fun ClientProgressDashboardCard(
    clientName: String, 
    logs: List<ClientProgressLog>,
    onShowWorkouts: () -> Unit,
    onEditNote: (Int, String) -> Unit
) {
    val chronologicalLogs = logs
    val latestLog = logs.lastOrNull()

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
                        val daysAgo = latestLog?.logDate?.let { getDaysAgoText(it) } ?: ""
                        Text(text = "Ostatni pomiar: ${latestLog?.logDate ?: "Brak"} $daysAgo", fontSize = 12.sp, color = Color.Gray)
                    }
                }
                if (latestLog != null) {
                    Text(text = "${latestLog.weight} kg", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = Green80)
                }
            }

            if (chronologicalLogs.isNotEmpty()) {
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

            if (latestLog != null) {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = { onEditNote(latestLog.id, latestLog.notes ?: "") }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (latestLog.notes.isNullOrBlank()) "Dodaj notatkę do pomiaru" else "Edytuj notatkę")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onShowWorkouts, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5))) {
                Text("Historia ćwiczeń klienta", color = Color.White)
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
        val xStep = if (weights.size > 1) width / (weights.size - 1) else 0f

        val path = Path()
        weights.forEachIndexed { index, weight ->
            val x = if (weights.size == 1) width / 2 else index * xStep
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
    onShowExerciseChart: (String) -> Unit,
    onShowSessionExecution: () -> Unit,
    onSendToClient: () -> Unit,
    onDeleteSession: () -> Unit
) {
    val isCompleted = session.status == "COMPLETED"
    val isDraft = session.status == "DRAFT"
    val bgColor = if (isCompleted) Color(0xFFF9F9F9) else Color.White
    val cardAlpha = if (isCompleted) 0.8f else 1f

    Card(
        modifier = Modifier.fillMaxWidth().alpha(cardAlpha),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isCompleted) 0.dp else 2.dp),
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
                    if (session.clientName != null) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(text = "Dla: ${session.clientName}", fontSize = 13.sp, color = Color(0xFF1E88E5), fontWeight = FontWeight.Medium)
                    }
                    if (isDraft) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("📝 Szkic (niewysłany)", color = Color(0xFFFFA000), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    } else if (isCompleted) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("✅ Zrealizowany przez klienta", color = Color(0xFF4CAF50), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("⏳ Oczekuje na realizację", color = Color.Gray, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
                if (!isCompleted) {
                    Row {
                        IconButton(onClick = onDeleteSession) {
                            Icon(Icons.Filled.Delete, contentDescription = "Usuń trening", tint = Color.Red)
                        }
                        IconButton(onClick = onAddExercise) {
                            Icon(Icons.Filled.Add, contentDescription = "Dodaj ćwiczenie", tint = Color(0xFF1E88E5))
                        }
                    }
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
                        if (!isCompleted) {
                            IconButton(onClick = { onDeleteExercise(ex.id) }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Filled.Delete, contentDescription = "Usuń", tint = Color.Red)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (isDraft) {
                    Button(onClick = onSendToClient, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))) {
                        Text("Wyślij trening do klienta", color = Color.White)
                    }
                } else {
                    Button(onClick = onShowSessionExecution, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5))) {
                        Text("Oceń wykonanie klienta", color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun EditNoteDialog(
    initialNote: String,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    var notes by remember { mutableStateOf(initialNote) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Notatka do pomiaru") },
        text = {
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notatki") },
                    modifier = Modifier.fillMaxWidth()
                )
        },
        confirmButton = {
            Button(onClick = { onSubmit(notes) }) { Text("Zapisz") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } }
    )
}

@Composable
fun SessionExecutionDialog(
    session: ClientTrainingSession,
    workouts: List<ClientWorkoutDto>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Realizacja: ${session.title}") },
        text = {
            if (workouts.isEmpty()) {
                Text("Klient nie zapisał jeszcze żadnych wyników powiązanych z tą sesją.", color = Color.Gray)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(workouts) { w ->
                        val isTimeBased = w.exerciseName.contains("Deska", ignoreCase = true) || w.exerciseName.contains("Plank", ignoreCase = true)
                        val repsLabel = if (isTimeBased) "sek." else "powt."
                        Text("• ${w.exerciseName} - Seria ${w.sets}: ${w.weight} kg x ${w.reps} $repsLabel", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Zamknij", color = Color(0xFF1E88E5)) } }
    )
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSessionDialog(
    clients: List<UserResponse>,
    onDismiss: () -> Unit,
    onSubmit: (clientId: Int, title: String, startTime: String, duration: Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var selectedClient by remember { mutableStateOf<UserResponse?>(clients.firstOrNull()) }
    var title by remember { mutableStateOf("") }
    var startTime by remember { mutableStateOf("") }
    var durationStr by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Zaplanuj trening") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                    OutlinedTextField(
                        value = selectedClient?.let { "${it.firstName} ${it.lastName}" } ?: "Brak",
                        onValueChange = {}, readOnly = true, label = { Text("Przypisz dla") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        clients.forEach { client ->
                            DropdownMenuItem(text = { Text("${client.firstName} ${client.lastName}") }, onClick = { selectedClient = client; expanded = false })
                        }
                    }
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Nazwa treningu") },
                    modifier = Modifier.fillMaxWidth()
                )
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = startTime,
                        onValueChange = { },
                        readOnly = true,
                        label = { Text("Data treningu") },
                        placeholder = { Text("Wybierz datę") },
                        isError = errorMessage != null,
                        trailingIcon = {
                            Icon(Icons.Filled.DateRange, contentDescription = "Wybierz datę", tint = Color(0xFF1E88E5))
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { showDatePicker = true }
                    )
                }
                if (errorMessage != null) {
                    Text(text = errorMessage!!, color = Color.Red, fontSize = 12.sp)
                }
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
                val regex = Regex("""^\d{4}-\d{2}-\d{2}$""")
                if (selectedClient != null && title.isNotBlank() && startTime.isNotBlank() && d != null) {
                    if (!startTime.matches(regex)) {
                        errorMessage = "Wymagany format daty to: YYYY-MM-DD"
                    } else {
                        val formattedTime = "$startTime 00:00:00"
                        onSubmit(selectedClient!!.id, title, formattedTime, d)
                    }
                }
            }) { Text("Zapisz") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } }
    )

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                        // DatePicker zwraca czas w strefie UTC
                        sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                        startTime = sdf.format(java.util.Date(millis))
                        errorMessage = null
                    }
                    showDatePicker = false
                }) { Text("Wybierz") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Anuluj") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
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
    var selectedExercise by remember { mutableStateOf<String?>(workouts.firstOrNull()?.exerciseName) }
    val uniqueExercises = workouts.map { it.exerciseName }.distinct()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Historia treningów: $clientName", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (workouts.isEmpty()) {
                    Text("Klient nie posiada jeszcze żadnych wpisów.", color = Color.Gray)
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
                    
                    val exerciseLogs = workouts.filter { it.exerciseName == selectedExercise }.sortedByDescending { it.id }
                    LazyColumn(modifier = Modifier.heightIn(max = 250.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(exerciseLogs) { log ->
                            val isTimeBased = log.exerciseName.contains("Deska", ignoreCase = true) || log.exerciseName.contains("Plank", ignoreCase = true)
                            val repsLabel = if (isTimeBased) "sek." else "powt."
                            Text("• ${log.date} - Seria ${log.sets}: ${log.weight} kg x ${log.reps} $repsLabel", fontSize = 14.sp)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Zamknij", color = Color(0xFF1E88E5)) } }
    )
}

@Composable
private fun ProgressBottomNav(onNavigateToHome: () -> Unit = {}, onNavigateToClients: () -> Unit, onNavigateToAccount: () -> Unit = {}) {
    NavigationBar(
        containerColor = Color.White,
        tonalElevation = 8.dp
    ) {
        NavigationBarItem(
            selected = false,
            onClick = onNavigateToHome,
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
            onClick = onNavigateToAccount,
            label = { Text("Konto") },
            icon = { Icon(Icons.Filled.AccountCircle, contentDescription = "Konto") }
        )
    }
}