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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.ui.platform.LocalContext
import java.util.Calendar

const val EXTRA_PLAN_CLIENT_ID = "PLAN_CLIENT_ID"
const val EXTRA_PLAN_CLIENT_NAME = "PLAN_CLIENT_NAME"

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
data class ClientWorkout(val id: Int, val sessionId: Int, val clientName: String, val details: String) // Dodany pomocniczy model
data class DraftExercise(val exerciseId: Int, val name: String, val sets: Int, val reps: Int, val weight: Double)

data class ClientTrainingSession(
    val id: Int,
    val title: String,
    val date: String,
    val duration: String,
    val clientName: String?,
    val status: String?
)

private fun formatDisplayDate(dateString: String?): String {
    if (dateString.isNullOrBlank() || dateString.equals("null", ignoreCase = true)) return ""
    var s = dateString.trim()
    
    android.util.Log.d("DATE_DEBUG", "formatDisplayDate INPUT: '$dateString'")
    
    if (Regex("^\\d{2}\\.\\d{2}\\.\\d{4}$").matches(s)) {
        android.util.Log.d("DATE_DEBUG", "formatDisplayDate -> already DD.MM.YYYY: '$s'")
        return s
    }
    
    if (s.contains(" ")) {
        s = s.substringBefore(" ")
        android.util.Log.d("DATE_DEBUG", "formatDisplayDate -> after strip time: '$s'")
    }
    
    val isoMatch = Regex("^(\\d{4})-(\\d{2})-(\\d{2})$").find(s)
    if (isoMatch != null) {
        val (year, month, day) = isoMatch.destructured
        val result = "$day.$month.$year"
        android.util.Log.d("DATE_DEBUG", "formatDisplayDate -> ISO match: '$result'")
        return result
    }
    
    val slashMatch = Regex("^(\\d{4})/(\\d{2})/(\\d{2})$").find(s)
    if (slashMatch != null) {
        val (year, month, day) = slashMatch.destructured
        val result = "$day.$month.$year"
        android.util.Log.d("DATE_DEBUG", "formatDisplayDate -> slash match: '$result'")
        return result
    }
    
    val euSlashMatch = Regex("^(\\d{2})/(\\d{2})/(\\d{4})$").find(s)
    if (euSlashMatch != null) {
        val (day, month, year) = euSlashMatch.destructured
        val result = "$day.$month.$year"
        android.util.Log.d("DATE_DEBUG", "formatDisplayDate -> EU slash match: '$result'")
        return result
    }
    
    val digits = s.replace(Regex("[^0-9]"), "")
    android.util.Log.d("DATE_DEBUG", "formatDisplayDate -> fallback digits: '$digits' from s='$s'")
    if (digits.length >= 8) {
        val d1 = digits.substring(0, 2).toIntOrNull() ?: 0
        val d2 = digits.substring(2, 4).toIntOrNull() ?: 0
        val d3 = digits.substring(4, 6).toIntOrNull() ?: 0
        val d4 = digits.substring(6, 8).toIntOrNull() ?: 0
        if (d1 in 20..99 && d2 in 1..12 && d3 in 1..31) {
            val year = if (d1 < 100) 2000 + d1 else d1
            val result = "${d3.toString().padStart(2, '0')}.${d2.toString().padStart(2, '0')}.${year}"
            android.util.Log.d("DATE_DEBUG", "formatDisplayDate -> fallback yyyyMMdd: '$result'")
            return result
        }
        if (d3 in 20..99 && d2 in 1..12 && d1 in 1..31) {
            val result = "${d1.toString().padStart(2, '0')}.${d2.toString().padStart(2, '0')}.20${d3}"
            android.util.Log.d("DATE_DEBUG", "formatDisplayDate -> fallback ddMMyyyy: '$result'")
            return result
        }
        if (d1 in 1..31 && d2 in 1..12 && (d3 in 20..99 || d3 in 2020..2100)) {
            val year = if (d3 < 100) 2000 + d3 else d3
            val result = "${d1.toString().padStart(2, '0')}.${d2.toString().padStart(2, '0')}.$year"
            android.util.Log.d("DATE_DEBUG", "formatDisplayDate -> fallback ddMMyyyy2: '$result'")
            return result
        }
    }
    
    android.util.Log.d("DATE_DEBUG", "formatDisplayDate -> NO MATCH, returning raw: '$s'")
    return s
}

private fun getDaysAgoText(dateString: String): String {
    val match = Regex("(\\d{2})\\.(\\d{2})\\.(\\d{4})").find(dateString) ?: return ""
    val (dayStr, monthStr, yearStr) = match.destructured
    val day = dayStr.toIntOrNull() ?: return ""
    val month = monthStr.toIntOrNull() ?: return ""
    val year = yearStr.toIntOrNull() ?: return ""
    return try {
        val cal = java.util.Calendar.getInstance()
        cal.set(year, month - 1, day, 0, 0, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        val logTime = cal.timeInMillis
        val today = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        val days = java.util.concurrent.TimeUnit.MILLISECONDS.toDays(today - logTime)
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
        val initialPlanClientId = intent.getIntExtra(EXTRA_PLAN_CLIENT_ID, -1).takeIf { it > 0 }
        val initialPlanClientName = intent.getStringExtra(EXTRA_PLAN_CLIENT_NAME)

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
                        viewModel = viewModel,
                        initialPlanClientId = initialPlanClientId,
                        initialPlanClientName = initialPlanClientName
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
    viewModel: TrainerProgressViewModel,
    initialPlanClientId: Int? = null,
    initialPlanClientName: String? = null
) {
    var selectedTab by remember { mutableIntStateOf(0) } 
    var selectedSubTab by remember { mutableIntStateOf(0) } 

    var expandedFilter by remember { mutableStateOf(false) }
    var selectedFilterName by remember { mutableStateOf<String?>(null) }
    val uniqueClients = state.clients
        .map { "${it.firstName} ${it.lastName}".trim() }
        .distinct()
        .sorted()

    val logsByClient = state.progressLogs.groupBy { it.clientName }
    val progressClients = state.clients.map { client ->
        val fullName = "${client.firstName} ${client.lastName}".trim()
        fullName to logsByClient[fullName].orEmpty()
    }
    var showEditNoteDialog by remember { mutableStateOf<Pair<Int, String>?>(null) }
    var showSessionDialog by remember { mutableStateOf(initialPlanClientId != null) }
    var selectedSessionForExercise by remember { mutableStateOf<ClientTrainingSession?>(null) }
    var selectedChartExercise by remember { mutableStateOf<String?>(null) }
    var selectedSessionForExecution by remember { mutableStateOf<ClientTrainingSession?>(null) }
    var showClientWorkoutsFor by remember { mutableStateOf<String?>(null) }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatBox("Podopieczni", state.clients.size.toString())
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
                if (progressClients.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Text(
                                text = "Brak przypisanych podopiecznych.",
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                textAlign = TextAlign.Center,
                                color = Color.Gray
                            )
                        }
                    }
                }

                items(progressClients) { (clientName, logs) ->
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
                exercises = state.availableExercises,
                initialClientId = initialPlanClientId,
                initialClientName = initialPlanClientName,
                onDismiss = { showSessionDialog = false },
                onSubmit = { clientId, title, startTime, duration, drafts, sendImmediately ->
                    viewModel.addSessionWithExercises(clientId, title, startTime, duration, drafts, sendImmediately)
                    showSessionDialog = false
                }
            )
        }

        if (selectedSessionForExercise != null) {
            AddSessionExerciseDialog(
                exercises = state.availableExercises,
                onDismiss = { selectedSessionForExercise = null },
                onSubmitMultiple = { drafts ->
                    viewModel.addMultipleSessionExercises(selectedSessionForExercise!!.id, drafts)
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
                workouts = state.clientWorkouts,
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
                        val displayDate = latestLog?.logDate?.let { formatDisplayDate(it) } ?: "Brak"
                        val daysAgo = latestLog?.logDate?.let { getDaysAgoText(it) } ?: ""
                        
                        // ZAKOŃCZENIE KONFLIKTU: Poprawnie sformatowana data z fallbackiem dla braku logów
                        Text(
                            text = if (latestLog != null) "Ostatni pomiar: $displayDate $daysAgo" else "Brak pomiarów", 
                            fontSize = 12.sp, 
                            color = Color.Gray
                        )
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
            } else {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Ten podopieczny nie ma jeszcze żadnych pomiarów.",
                    fontSize = 13.sp,
                    color = Color.Gray
                )
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
                color = Color(0xFF1E88E5), 
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
                    Text(text = "Termin: ${formatDisplayDate(session.date)}", fontSize = 14.sp, color = Color.Gray)
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
            
            // NAPRAWIONE I DOKOŃCZONE UCIĘTE MIEJSCE KODU
            if (exercises.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color(0xFFE0E0E0))
                exercises.forEach { ex ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onShowExerciseChart(ex.exerciseName) }
                        ) {
                            Text(text = ex.exerciseName, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(text = "${ex.sets} serii x ${ex.reps} powt. @ ${ex.weight} kg", fontSize = 12.sp, color = Color.Gray)
                        }
                        if (!isCompleted) {
                            IconButton(onClick = { onDeleteExercise(ex.id) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Usuń ćwiczenie", tint = Color.Gray, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
            
            // Dolny panel akcji w karcie sesji
            if (isDraft) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onSendToClient,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                ) {
                    Text("Wyślij plan treningowy do klienta", color = Color.White)
                }
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onShowSessionExecution,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isCompleted) "Zobacz wyniki treningu" else "Sprawdź status realizacji")
                }
            }
        }
    }
}

// --- STOPKI I DIALOGI (Uzupełnienie brakujących komponentów dla poprawnej kompilacji pliku) ---

@Composable
fun ProgressBottomNav(onNavigateToHome: () -> Unit, onNavigateToClients: () -> Unit, onNavigateToAccount: () -> Unit) {
    NavigationBar {
        NavigationBarItem(
            selected = false, onClick = onNavigateToHome,
            icon = { Icon(Icons.Filled.Home, contentDescription = "Home") }, label = { Text("Główna") }
        )
        NavigationBarItem(
            selected = true, onClick = onNavigateToClients,
            icon = { Icon(Icons.Filled.Person, contentDescription = "Klienci") }, label = { Text("Postępy") }
        )
        NavigationBarItem(
            selected = false, onClick = onNavigateToAccount,
            icon = { Icon(Icons.Filled.AccountCircle, contentDescription = "Konto") }, label = { Text("Konto") }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditNoteDialog(initialNote: String, onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var note by remember { mutableStateOf(initialNote) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Notatka do pomiaru") },
        text = {
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Treść notatki") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )
        },
        confirmButton = { Button(onClick = { onSubmit(note) }) { Text("Zapisz") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSessionDialog(
    clients: List<UserResponse>,
    exercises: List<ClientExercise>,
    initialClientId: Int?,
    initialClientName: String?,
    onDismiss: () -> Unit,
    onSubmit: (Int, String, String, Int, List<DraftExercise>, Boolean) -> Unit
) {
    // --- 1. Informacje o treningu ---
    var title by remember { mutableStateOf("") }
    var startTime by remember { mutableStateOf("") }
    var expandedClient by remember { mutableStateOf(false) }
    var selectedClientId by remember { mutableStateOf(initialClientId) }
    var clientSearchQuery by remember { mutableStateOf(initialClientName ?: "") }

    // --- 2. Informacje o dodawanym ćwiczeniu ---
    var expandedExercise by remember { mutableStateOf(false) }
    var selectedExerciseId by remember { mutableStateOf<Int?>(null) }
    var exerciseSearchQuery by remember { mutableStateOf("") }
    var sets by remember { mutableStateOf("") }
    var reps by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }

    val draftList = remember { mutableStateListOf<DraftExercise>() }
    val scrollState = rememberScrollState()

    val filteredClients = clients.filter { "${it.firstName} ${it.lastName}".contains(clientSearchQuery, ignoreCase = true) }
    val filteredExercises = exercises.filter { it.name.contains(exerciseSearchQuery, ignoreCase = true) }

    val context = LocalContext.current
    val calendar = Calendar.getInstance()

    val datePickerDialog = DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            startTime = String.format("%04d-%02d-%02d", year, month + 1, dayOfMonth)
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Kreator nowego treningu", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("1. Podstawowe informacje", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Green80)
                
                ExposedDropdownMenuBox(expanded = expandedClient, onExpandedChange = { expandedClient = !expandedClient }) {
                    OutlinedTextField(
                        value = clientSearchQuery,
                        onValueChange = { clientSearchQuery = it; expandedClient = true; selectedClientId = null },
                        label = { Text("Wyszukaj i wybierz klienta") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedClient) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    if (filteredClients.isNotEmpty() && expandedClient) {
                        ExposedDropdownMenu(expanded = expandedClient, onDismissRequest = { expandedClient = false }) {
                            filteredClients.forEach { client ->
                                DropdownMenuItem(
                                    text = { Text("${client.firstName} ${client.lastName}") },
                                    onClick = { selectedClientId = client.id; clientSearchQuery = "${client.firstName} ${client.lastName}"; expandedClient = false }
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Tytuł treningu (np. 'Trening Pull')") },
                    modifier = Modifier.fillMaxWidth()
                )

                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = startTime,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Data treningu") },
                        trailingIcon = { Icon(Icons.Filled.DateRange, contentDescription = "Wybierz datę") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Box(modifier = Modifier.matchParentSize().clickable { datePickerDialog.show() })
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text("2. Dodaj ćwiczenia do planu", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Green80)

                ExposedDropdownMenuBox(
                    expanded = expandedExercise,
                    onExpandedChange = { expandedExercise = !expandedExercise }
                ) {
                    OutlinedTextField(
                        value = exerciseSearchQuery,
                        onValueChange = { exerciseSearchQuery = it; expandedExercise = true; selectedExerciseId = null },
                        label = { Text("Wyszukaj ćwiczenie...") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedExercise) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    if (filteredExercises.isNotEmpty() && expandedExercise) {
                        ExposedDropdownMenu(
                            expanded = expandedExercise,
                            onDismissRequest = { expandedExercise = false }
                        ) {
                            filteredExercises.forEach { exercise ->
                                DropdownMenuItem(
                                    text = { Text(exercise.name) },
                                    onClick = {
                                        selectedExerciseId = exercise.id
                                        exerciseSearchQuery = exercise.name
                                        expandedExercise = false
                                    }
                                )
                            }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = sets,
                        onValueChange = { sets = it },
                        label = { Text("Serie") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = reps,
                        onValueChange = { reps = it },
                        label = { Text("Powt.") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = weight,
                        onValueChange = { weight = it },
                        label = { Text("Ciężar") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }

                Button(
                    onClick = {
                        val exId = selectedExerciseId
                        val s = sets.toIntOrNull()
                        val r = reps.toIntOrNull()
                        val w = weight.replace(",", ".").toDoubleOrNull() ?: 0.0
                        if (exId != null && s != null && r != null) {
                            val exName = exercises.find { it.id == exId }?.name ?: "Nieznane"
                            draftList.add(DraftExercise(exId, exName, s, r, w))
                            selectedExerciseId = null
                            exerciseSearchQuery = ""
                            sets = ""
                            reps = ""
                            weight = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5))
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Dodaj", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Dodaj ćwiczenie do listy")
                }

                if (draftList.isNotEmpty()) {
                    Text("Zaplanowane ćwiczenia (${draftList.size}):", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        draftList.forEach { draft ->
                            Row(
                                modifier = Modifier.fillMaxWidth().background(Color(0xFFF5F5F5), RoundedCornerShape(8.dp)).padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(draft.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.DarkGray)
                                    Text("${draft.sets}x${draft.reps} @ ${draft.weight}kg", fontSize = 12.sp, color = Color.Gray)
                                }
                                IconButton(
                                    onClick = { draftList.remove(draft) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Usuń", tint = Color.Red, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    val finalDrafts = draftList.toMutableList()
                    val exId = selectedExerciseId
                    val s = sets.toIntOrNull()
                    val r = reps.toIntOrNull()
                    val w = weight.replace(",", ".").toDoubleOrNull() ?: 0.0
                    
                    if (exId != null && s != null && r != null) {
                        val exName = exercises.find { it.id == exId }?.name ?: "Nieznane"
                        finalDrafts.add(DraftExercise(exId, exName, s, r, w))
                    }

                    val clientId = selectedClientId
                    if (clientId != null && title.isNotBlank() && startTime.isNotBlank()) {
                        val time = if (startTime.contains("T")) startTime else "${startTime}T12:00:00"
                        onSubmit(clientId, title, time, 60, finalDrafts, false)
                    }
                }) { Text("Zapisz jako szkic") }
                
                Button(onClick = {
                    val finalDrafts = draftList.toMutableList()
                    val exId = selectedExerciseId
                    val s = sets.toIntOrNull()
                    val r = reps.toIntOrNull()
                    val w = weight.replace(",", ".").toDoubleOrNull() ?: 0.0
                    
                    if (exId != null && s != null && r != null) {
                        val exName = exercises.find { it.id == exId }?.name ?: "Nieznane"
                        finalDrafts.add(DraftExercise(exId, exName, s, r, w))
                    }

                    val clientId = selectedClientId
                    if (clientId != null && title.isNotBlank() && startTime.isNotBlank() && finalDrafts.isNotEmpty()) {
                        val time = if (startTime.contains("T")) startTime else "${startTime}T12:00:00"
                        onSubmit(clientId, title, time, 60, finalDrafts, true)
                    }
                }, colors = ButtonDefaults.buttonColors(containerColor = Green80)) { 
                    Text("Wyślij do klienta") 
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj", color = Color.Gray) } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSessionExerciseDialog(
    exercises: List<ClientExercise>,
    onDismiss: () -> Unit,
    onSubmitMultiple: (List<DraftExercise>) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var selectedExerciseId by remember { mutableStateOf<Int?>(null) }
    var exerciseSearchQuery by remember { mutableStateOf("") }
    var sets by remember { mutableStateOf("") }
    var reps by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }

    val draftList = remember { mutableStateListOf<DraftExercise>() }
    val filteredExercises = exercises.filter { it.name.contains(exerciseSearchQuery, ignoreCase = true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Kreator planu ćwiczeń", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                            value = exerciseSearchQuery,
                            onValueChange = { exerciseSearchQuery = it; expanded = true; selectedExerciseId = null },
                            label = { Text("Wyszukaj ćwiczenie...") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                        if (filteredExercises.isNotEmpty() && expanded) {
                            ExposedDropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                filteredExercises.forEach { exercise ->
                                    DropdownMenuItem(
                                        text = { Text(exercise.name) },
                                        onClick = {
                                            selectedExerciseId = exercise.id
                                            exerciseSearchQuery = exercise.name
                                            expanded = false
                                        }
                                    )
                                }
                            }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = sets,
                        onValueChange = { sets = it },
                        label = { Text("Serie") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = reps,
                        onValueChange = { reps = it },
                        label = { Text("Powt.") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = weight,
                        onValueChange = { weight = it },
                        label = { Text("Ciężar") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
                Button(
                    onClick = {
                        val exId = selectedExerciseId
                        val s = sets.toIntOrNull()
                        val r = reps.toIntOrNull()
                        val w = weight.replace(",", ".").toDoubleOrNull() ?: 0.0
                        if (exId != null && s != null && r != null) {
                            val exName = exercises.find { it.id == exId }?.name ?: "Nieznane"
                            draftList.add(DraftExercise(exId, exName, s, r, w))
                            selectedExerciseId = null
                            exerciseSearchQuery = ""
                            sets = ""
                            reps = ""
                            weight = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5))
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Dodaj", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Dodaj do listy")
                }

                if (draftList.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text("Na liście do zapisu (${draftList.size}):", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 160.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(draftList) { draft ->
                            Row(
                                modifier = Modifier.fillMaxWidth().background(Color(0xFFF5F5F5), RoundedCornerShape(8.dp)).padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(draft.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.DarkGray)
                                    Text("${draft.sets}x${draft.reps} @ ${draft.weight}kg", fontSize = 12.sp, color = Color.Gray)
                                }
                                IconButton(
                                    onClick = { draftList.remove(draft) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Usuń", tint = Color.Red, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val finalDrafts = draftList.toMutableList()
                val exId = selectedExerciseId
                val s = sets.toIntOrNull()
                val r = reps.toIntOrNull()
                val w = weight.replace(",", ".").toDoubleOrNull() ?: 0.0
                
                // Jeżeli użytkownik wpisał dane, ale zapomniał kliknąć "Dodaj do listy" przed zapisem, inteligentnie dodajemy to za niego
                if (exId != null && s != null && r != null) {
                    val exName = exercises.find { it.id == exId }?.name ?: "Nieznane"
                    finalDrafts.add(DraftExercise(exId, exName, s, r, w))
                }
                
                if (finalDrafts.isNotEmpty()) {
                    onSubmitMultiple(finalDrafts)
                } else {
                    onDismiss()
                }
            }, colors = ButtonDefaults.buttonColors(containerColor = Green80)) { Text("Zapisz wszystko do planu") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj", color = Color.Gray) } }
    )
}

@Composable
fun TrainerExerciseChartDialog(
    exerciseName: String,
    sessionExercises: List<ClientSessionExercise>,
    trainingSessions: List<ClientTrainingSession>,
    onDismiss: () -> Unit
) {
    val relatedExercises = sessionExercises.filter { it.exerciseName == exerciseName }
    val dataPoints = relatedExercises.mapNotNull { ex ->
        val session = trainingSessions.find { it.id == ex.sessionId }
        if (session != null) session.date to ex.weight.toFloat() else null
    }.sortedBy { it.first }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Wykres zaleceń: $exerciseName", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (dataPoints.isEmpty()) {
                    Text("Brak danych historycznych z zaleceń.", color = Color.Gray)
                } else {
                    Text("Zalecony ciężar w przeszłości:", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                        items(dataPoints) { (date, weight) ->
                            Text("• $date - $weight kg", fontSize = 14.sp, color = Color.DarkGray, modifier = Modifier.padding(vertical = 2.dp))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Zamknij") } }
    )
}

@Composable
fun SessionExecutionDialog(session: ClientTrainingSession, workouts: List<ClientWorkoutDto>, onDismiss: () -> Unit) {
    val sessionDateStr = session.date.substringBefore("T").substringBefore(" ")
    val sessionClientName = session.clientName ?: ""
    val filteredWorkouts = workouts.filter { 
        it.sessionId == session.id || 
        (it.clientName == sessionClientName && it.date.substringBefore("T").substringBefore(" ") == sessionDateStr)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Realizacja treningu", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Trening: ${session.title}", fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(12.dp))
                if (filteredWorkouts.isEmpty()) {
                    Text("Klient jeszcze nie zapisał wyników lub data się nie zgadza.", color = Color.Gray)
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(filteredWorkouts) { workout ->
                            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))) {
                                Column(modifier = Modifier.padding(12.dp).fillMaxWidth()) {
                                    Text(workout.exerciseName, fontWeight = FontWeight.Bold, color = Color(0xFF1E88E5))
                                    Text("Zrobiono: ${workout.sets} serii x ${workout.reps} powt. @ ${workout.weight} kg", fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Zamknij") } }
    )
}

@Composable
fun ClientRealWorkoutsDialog(clientName: String, workouts: List<ClientWorkoutDto>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Historia ćwiczeń: $clientName", fontWeight = FontWeight.Bold) },
        text = {
            if (workouts.isEmpty()) {
                Text("Klient nie zapisał jeszcze żadnych wyników.", color = Color.Gray)
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(workouts.sortedByDescending { it.id }) { workout ->
                        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF9F9F9)), elevation = CardDefaults.cardElevation(1.dp)) {
                            Column(modifier = Modifier.padding(12.dp).fillMaxWidth()) {
                                Text(workout.exerciseName, fontWeight = FontWeight.Bold, color = Color(0xFF1E88E5))
                                Text("Wynik: ${workout.sets}x${workout.reps} @ ${workout.weight} kg", fontSize = 14.sp)
                                Text("Data: ${workout.date}", fontSize = 12.sp, color = Color.Gray)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Zamknij") } }
    )
}
