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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
data class ClientSessionExercise(val id: Int, val sessionId: Int, val exerciseId: Int, val exerciseName: String, val sets: Int, val reps: Int, val weight: Double)
data class AddSessionExerciseRequest(val exerciseId: Int, val sets: Int, val reps: Int, val weight: Double)
data class UpdateSessionRequest(val title: String, val startTime: String, val durationMinutes: Int)
data class UpdateSessionExerciseRequest(val sets: Int, val reps: Int, val weight: Double)
data class ClientWorkout(val id: Int, val sessionId: Int, val clientName: String, val details: String) // Dodany pomocniczy model
data class DraftExercise(
    val exerciseId: Int,
    val name: String,
    val sets: Int,
    val reps: Int,
    val weight: Double,
    val sessionExerciseId: Int? = null
)

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

    if (Regex("^\\d{2}\\.\\d{2}\\.\\d{4}$").matches(s)) return s
    
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
                    bottomBar = {
                        ProgressBottomNav(
                            onNavigateToHome = ::navigateToHome,
                            onNavigateToClients = ::navigateToClients,
                            onNavigateToProgress = { },
                            onNavigateToAccount = ::navigateToAccount
                        )
                    }
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
    var sessionEditorMode by remember { mutableStateOf<SessionEditorMode?>(null) }
    var selectedSessionForExercise by remember { mutableStateOf<ClientTrainingSession?>(null) }
    var selectedChartExercise by remember { mutableStateOf<String?>(null) }

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
            state.error?.let { errorMsg ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = errorMsg, color = Color(0xFFC62828), modifier = Modifier.weight(1f), fontSize = 13.sp)
                        TextButton(onClick = { viewModel.clearError() }) { Text("OK", color = Color(0xFFC62828)) }
                    }
                }
            }

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
                    val clientSessions = state.trainingSessions.filter { it.clientName == clientName }
                    val clientWorkouts = state.clientWorkouts.filter { it.clientName == clientName }
                    ClientProgressDashboardCard(
                        clientName = clientName,
                        logs = logs,
                        clientSessions = clientSessions,
                        clientWorkouts = clientWorkouts,
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
            state.error?.let { errorMsg ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(text = errorMsg, color = Color(0xFFC62828), modifier = Modifier.weight(1f), fontSize = 13.sp)
                        TextButton(onClick = { viewModel.clearError() }) { Text("OK", color = Color(0xFFC62828)) }
                    }
                }
            }

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
                        val sessionWorkouts = state.clientWorkouts.filter { it.sessionId == session.id }
                        TimelineSessionCard(
                            session = session,
                            exercises = sessionExercises,
                            sessionWorkouts = sessionWorkouts,
                            onAddExercise = { selectedSessionForExercise = session },
                            onEditDraft = {
                                sessionEditorMode = SessionEditorMode.EditDraft(session, sessionExercises)
                            },
                            onDeleteExercise = { viewModel.deleteSessionExercise(it) },
                            onShowExerciseChart = { selectedChartExercise = it },
                            onSendToClient = { viewModel.sendSessionToClient(session.id) },
                            onDeleteSession = { viewModel.deleteSession(session.id) }
                        )
                    }
                }
                item {
                    Button(
                        onClick = {
                            sessionEditorMode = SessionEditorMode.Create()
                        },
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

        if (showSessionDialog || sessionEditorMode != null) {
            val editorMode = sessionEditorMode ?: SessionEditorMode.Create(
                initialClientId = initialPlanClientId,
                initialClientName = initialPlanClientName
            )
            SessionEditorSheet(
                mode = editorMode,
                clients = state.clients,
                exercises = state.availableExercises,
                onDismiss = {
                    showSessionDialog = false
                    sessionEditorMode = null
                },
                onCreate = { clientId, title, startTime, duration, drafts, sendImmediately ->
                    viewModel.addSessionWithExercises(clientId, title, startTime, duration, drafts, sendImmediately)
                    showSessionDialog = false
                    sessionEditorMode = null
                },
                onUpdateDraft = { sessionId, title, startTime, duration, originalIds, drafts ->
                    viewModel.updateDraftSession(sessionId, title, startTime, duration, originalIds, drafts)
                    sessionEditorMode = null
                },
                onSendToClient = { sessionId ->
                    viewModel.sendSessionToClient(sessionId)
                },
                onUpdateAndSend = { sessionId, title, startTime, duration, originalIds, drafts ->
                    viewModel.updateAndSendDraftSession(sessionId, title, startTime, duration, originalIds, drafts)
                    sessionEditorMode = null
                }
            )
        }

        if (selectedSessionForExercise != null) {
            QuickAddExercisesSheet(
                exercises = state.availableExercises,
                onDismiss = { selectedSessionForExercise = null },
                onSubmit = { drafts ->
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
    clientSessions: List<ClientTrainingSession>,
    clientWorkouts: List<ClientWorkoutDto>,
    onEditNote: (Int, String) -> Unit
) {
    val chronologicalLogs = logs
    val latestLog = logs.lastOrNull()
    val completedCount = clientSessions.count { it.status == "COMPLETED" }
    val pendingCount = clientSessions.count { it.status == "CONFIRMED" }
    val weightDelta = if (chronologicalLogs.size >= 2) {
        chronologicalLogs.last().weight - chronologicalLogs.first().weight
    } else null

    val context = LocalContext.current
    val pdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) {
            ProgressReportPdfGenerator.write(
                context,
                uri,
                ProgressReportData.fromTrainerClient(clientName, logs, clientWorkouts, clientSessions)
            )
        }
    }

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
                        val displayDate = latestLog?.logDate?.let { ExercisePlanHelper.formatDisplayDate(it) } ?: "Brak"
                        val daysAgo = latestLog?.logDate?.let { getDaysAgoText(it) } ?: ""
                        Text(
                            text = if (latestLog != null) "Ostatni pomiar: $displayDate $daysAgo" else "Brak pomiarów",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                }
                if (latestLog != null) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(text = "${latestLog.weight} kg", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = Green80)
                        weightDelta?.let { delta ->
                            val sign = if (delta >= 0) "+" else ""
                            Text("$sign${"%.1f".format(delta)} kg łącznie", fontSize = 11.sp, color = Color.Gray)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProgressStatChip("Ukończone", completedCount.toString(), Color(0xFF4CAF50))
                ProgressStatChip("Oczekujące", pendingCount.toString(), Color(0xFFFFA000))
                ProgressStatChip("Pomiary", logs.size.toString(), Color(0xFF1E88E5))
            }

            if (chronologicalLogs.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = "Progresja wagi", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.SemiBold)
                WeightLineChart(logs = chronologicalLogs)
            }

            if (chronologicalLogs.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                ProgressTableSection(title = "Historia pomiarów") {
                    ProgressTableHeader(listOf("Data", "Waga", "Notatka"))
                    chronologicalLogs.takeLast(5).reversed().forEach { log ->
                        ProgressTableRow(
                            listOf(
                                ExercisePlanHelper.formatDisplayDate(log.logDate),
                                "${log.weight} kg",
                                log.notes?.take(20)?.let { if (it.length < (log.notes?.length ?: 0)) "$it…" else it } ?: "—"
                            )
                        )
                    }
                }
            }

            if (clientSessions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                ProgressTableSection(title = "Treningi") {
                    ProgressTableHeader(listOf("Nazwa", "Data", "Status"))
                    clientSessions.take(5).forEach { session ->
                        val statusLabel = when (session.status) {
                            "COMPLETED" -> "Zakończony"
                            "CONFIRMED" -> "Oczekuje"
                            "DRAFT" -> "Szkic"
                            else -> session.status ?: "—"
                        }
                        ProgressTableRow(
                            listOf(
                                session.title.take(18),
                                ExercisePlanHelper.formatDisplayDate(session.date),
                                statusLabel
                            )
                        )
                    }
                }
            }

            if (clientWorkouts.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                ProgressTableSection(title = "Ostatnie wykonane ćwiczenia") {
                    ProgressTableHeader(listOf("Data", "Ćwiczenie", "Wynik"))
                    clientWorkouts.takeLast(5).reversed().forEach { w ->
                        ProgressTableRow(
                            listOf(
                                ExercisePlanHelper.formatDisplayDate(w.date),
                                w.exerciseName.take(16),
                                ExercisePlanHelper.formatPlan(w.exerciseName, w.sets, w.reps, w.weight)
                            )
                        )
                    }
                }
            }

            if (chronologicalLogs.isEmpty() && clientSessions.isEmpty() && clientWorkouts.isEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text("Brak danych o postępach — klient nie logował jeszcze pomiarów ani treningów.", fontSize = 13.sp, color = Color.Gray)
            }

            if (latestLog != null) {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = { onEditNote(latestLog.id, latestLog.notes ?: "") }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (latestLog.notes.isNullOrBlank()) "Dodaj notatkę do pomiaru" else "Edytuj notatkę")
                }
            }

            if (chronologicalLogs.isNotEmpty() || clientWorkouts.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        val safeName = clientName.replace(" ", "_")
                        pdfLauncher.launch("Raport_${safeName}.pdf")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Generuj raport postępów (PDF)", color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun RowScope.ProgressStatChip(label: String, value: String, color: Color) {
    Card(
        modifier = Modifier.weight(1f),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontWeight = FontWeight.Bold, color = color, fontSize = 16.sp)
            Text(label, fontSize = 10.sp, color = Color.Gray)
        }
    }
}

@Composable
private fun ProgressTableSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Green80)
    Spacer(modifier = Modifier.height(6.dp))
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFAFAFA)),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp), content = content)
    }
}

@Composable
private fun ProgressTableHeader(cells: List<String>) {
    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        cells.forEachIndexed { i, cell ->
            Text(
                cell,
                modifier = Modifier.weight(if (i == 0) 1.1f else 1f),
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = Color.Gray
            )
        }
    }
    HorizontalDivider(color = Color(0xFFE0E0E0))
    Spacer(modifier = Modifier.height(4.dp))
}

@Composable
private fun ProgressTableRow(cells: List<String>) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        cells.forEachIndexed { i, cell ->
            Text(
                cell,
                modifier = Modifier.weight(if (i == 0) 1.1f else 1f),
                fontSize = 12.sp,
                color = Color.DarkGray
            )
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
    sessionWorkouts: List<ClientWorkoutDto> = emptyList(),
    onAddExercise: () -> Unit,
    onEditDraft: () -> Unit,
    onDeleteExercise: (Int) -> Unit,
    onShowExerciseChart: (String) -> Unit,
    onSendToClient: () -> Unit,
    onDeleteSession: () -> Unit
) {
    val isCompleted = session.status == "COMPLETED"
    val isDraft = session.status == "DRAFT"
    val isPending = session.status == "CONFIRMED"
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
                    Text(text = "Termin: ${ExercisePlanHelper.formatDisplayDate(session.date)}", fontSize = 14.sp, color = Color.Gray)
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
                        if (isDraft) {
                            IconButton(onClick = onEditDraft) {
                                Icon(Icons.Filled.Edit, contentDescription = "Edytuj szkic", tint = Green80)
                            }
                        }
                        IconButton(onClick = onDeleteSession) {
                            Icon(Icons.Filled.Delete, contentDescription = "Usuń trening", tint = Color.Red)
                        }
                        if (isDraft) {
                            IconButton(onClick = onAddExercise) {
                                Icon(Icons.Filled.Add, contentDescription = "Dodaj ćwiczenie", tint = Color(0xFF1E88E5))
                            }
                        }
                    }
                }
            }
            
            if (exercises.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color(0xFFE0E0E0))
                Text("Plan treningowy", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Green80)
                Spacer(modifier = Modifier.height(4.dp))
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
                            Text(
                                text = ExercisePlanHelper.formatPlan(ex.exerciseName, ex.sets, ex.reps, ex.weight),
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                        if (isDraft) {
                            IconButton(onClick = { onDeleteExercise(ex.id) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Usuń ćwiczenie", tint = Color.Gray, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            } else if (!isDraft) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Brak ćwiczeń w planie.", fontSize = 13.sp, color = Color.Gray)
            }

            if (isCompleted && sessionWorkouts.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = Color(0xFFE0E0E0))
                Spacer(modifier = Modifier.height(8.dp))
                Text("Wyniki klienta", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1E88E5))
                sessionWorkouts.forEach { w ->
                    Text(
                        "• ${w.exerciseName}: ${ExercisePlanHelper.formatPlan(w.exerciseName, w.sets, w.reps, w.weight)}",
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            } else if (isPending) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Plan wysłany — oczekuje na realizację przez klienta.",
                    fontSize = 12.sp,
                    color = Color(0xFF607D8B),
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
            }
            
            if (isDraft) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = onEditDraft,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Edytuj plan")
                    }
                    Button(
                        onClick = onSendToClient,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Wyślij", color = Color.White)
                    }
                }
            }
        }
    }
}

// --- STOPKI I DIALOGI (Uzupełnienie brakujących komponentów dla poprawnej kompilacji pliku) ---

@Composable
fun ProgressBottomNav(
    onNavigateToHome: () -> Unit,
    onNavigateToClients: () -> Unit,
    onNavigateToProgress: () -> Unit,
    onNavigateToAccount: () -> Unit
) {
    NavigationBar(
        containerColor = Color.White,
        tonalElevation = 8.dp
    ) {
        NavigationBarItem(
            selected = false,
            onClick = onNavigateToHome,
            icon = { Icon(Icons.Filled.Home, contentDescription = "Panel") },
            label = { Text("Panel") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Green80,
                selectedTextColor = Green80,
                indicatorColor = LightGreen80
            )
        )
        NavigationBarItem(
            selected = false,
            onClick = onNavigateToClients,
            icon = { Icon(Icons.Filled.Person, contentDescription = "Podopieczni") },
            label = { Text("Podopieczni") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Green80,
                selectedTextColor = Green80,
                indicatorColor = LightGreen80
            )
        )
        NavigationBarItem(
            selected = true,
            onClick = onNavigateToProgress,
            icon = { Icon(Icons.Filled.Edit, contentDescription = "Postęp") },
            label = { Text("Postęp") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Green80,
                selectedTextColor = Green80,
                indicatorColor = LightGreen80
            )
        )
        NavigationBarItem(
            selected = false,
            onClick = onNavigateToAccount,
            icon = { Icon(Icons.Filled.AccountCircle, contentDescription = "Konto") },
            label = { Text("Konto") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Green80,
                selectedTextColor = Green80,
                indicatorColor = LightGreen80
            )
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
                                    Text(
                                        ExercisePlanHelper.formatPlan(workout.exerciseName, workout.sets, workout.reps, workout.weight),
                                        fontSize = 14.sp
                                    )
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
                                Text("Wynik: ${ExercisePlanHelper.formatPlan(workout.exerciseName, workout.sets, workout.reps, workout.weight)}", fontSize = 14.sp)
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
