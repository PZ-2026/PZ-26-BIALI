package biali.fitmanager

import android.content.Intent
import android.os.Bundle
import android.content.Context
import android.net.Uri
import android.graphics.pdf.PdfDocument
import android.graphics.Paint
import android.graphics.Typeface
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import biali.fitmanager.network.ApiResult
import biali.fitmanager.network.FitManagerRepository
import biali.fitmanager.network.MembershipResponse
import biali.fitmanager.network.MembershipTypeResponse
import biali.fitmanager.network.AssignedSessionDto
import biali.fitmanager.network.CompleteSessionRequest
import biali.fitmanager.network.SetLogDto
import biali.fitmanager.LogWorkoutRequest
import biali.fitmanager.network.SessionManager
import biali.fitmanager.ui.theme.Green80
import biali.fitmanager.ui.theme.LightGreen80
import biali.fitmanager.ui.theme.GymManagerTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class HomeActivity : ComponentActivity() {
    private val repository = FitManagerRepository()
    private var displayName by mutableStateOf("Użytkowniku")
    private var userRole by mutableStateOf<String?>(null)
    private var membership by mutableStateOf<MembershipResponse?>(null)
    private var isMembershipLoading by mutableStateOf(true)
    private var balance by mutableStateOf<Double?>(null)
    private var membershipTypes by mutableStateOf<List<MembershipTypeResponse>>(emptyList())
    private var trainerName by mutableStateOf<String?>(null)
    private var trainerStartDate by mutableStateOf<String?>(null)
    private var trainerEndDate by mutableStateOf<String?>(null)
    private var currentTrainerId by mutableStateOf<Int?>(null)
    private var isTrainerLoading by mutableStateOf(true)
    private var assignedSessions by mutableStateOf<List<AssignedSessionDto>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SessionManager.initialize(applicationContext)
        enableEdgeToEdge()

        val token = SessionManager.getToken()
        val loggedEmail = intent.getStringExtra("USER_EMAIL")
            ?: token?.let(SessionManager::resolveEmailFromToken)
        val loggedDisplayName = token?.let(SessionManager::resolveDisplayNameFromToken)

        userRole = SessionManager.getRole() ?: token?.let(SessionManager::resolveRoleFromToken)
        displayName = loggedDisplayName ?: displayNameFromEmail(loggedEmail)
        fetchProfileDisplayNameIfNeeded()
        fetchMembership()
        fetchBalance()
        fetchMembershipTypes()
        fetchTrainerStatus()
        fetchAssignedSessions()

        setContent {
            GymManagerTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = { Navbar(onLogout = ::logout, balance = balance, onBalanceClick = ::navigateToWallet, userRole = userRole) },
                    bottomBar = {
                        FitBottomNav(
                            currentRoute = "home",
                            onNavigateToHome = { /* Już tu jesteśmy */ },
                            onNavigateToTrainers = ::navigateToTrainers,
                            onNavigateToMemberships = ::navigateToMemberships,
                            onNavigateToProgress = ::navigateToProgress,
                            onNavigateToAccount = ::navigateToAccount
                        )
                    }
                ) { innerPadding ->
                    MainContent(
                        modifier = Modifier.padding(innerPadding),
                        displayName = displayName,
                        membership = membership,
                        isMembershipLoading = isMembershipLoading,
                        membershipTypes = membershipTypes,
                        onBuyMembership = ::navigateToMemberships,
                        trainerName = trainerName,
                        trainerStartDate = trainerStartDate,
                        trainerEndDate = trainerEndDate,
                        isTrainerLoading = isTrainerLoading,
                        onTrainerClick = {
                            currentTrainerId?.let { navigateToTrainerDetails(it) }
                        },
                        onNavigateToProgress = ::navigateToProgress,
                        assignedSessions = assignedSessions,
                        onCompleteSession = ::completeSession
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // refresh critical user data when returning to Home
        fetchBalance()
        fetchMembership()
        fetchTrainerStatus()
        fetchAssignedSessions()
    }

    private fun fetchBalance() {
        lifecycleScope.launch {
            when (val result = repository.getMe()) {
                is ApiResult.Success -> {
                    balance = result.data.balance ?: SessionManager.getBalance()
                }
                is ApiResult.Unauthorized -> logout()
                is ApiResult.Error -> {
                    // keep local stored balance if available
                    balance = SessionManager.getBalance()
                }
            }
        }
    }

    private fun fetchAssignedSessions() {
        lifecycleScope.launch {
            when (val result = repository.getMyAssignedSessions()) {
                is ApiResult.Success -> assignedSessions = result.data
                else -> Unit
            }
        }
    }

    private fun completeSession(sessionId: Int, request: CompleteSessionRequest) {
        lifecycleScope.launch {
            when (val result = repository.completeSession(sessionId, request)) {
                is ApiResult.Success -> fetchAssignedSessions() // odśwież widok treningów
                else -> Unit
            }
        }
    }

    private fun navigateToWallet() {
        val intent = Intent(this, WalletActivity::class.java)
        startActivity(intent)
    }

    private fun fetchProfileDisplayNameIfNeeded() {
        if (displayName != "Użytkowniku") return

        lifecycleScope.launch {
            when (val result = repository.getMe()) {
                is ApiResult.Success -> {
                    val fromParts = listOf(result.data.firstName, result.data.lastName)
                        .filter { it.isNotBlank() }
                        .joinToString(" ")

                    displayName = when {
                        fromParts.isNotBlank() -> fromParts
                        !result.data.name.isNullOrBlank() -> result.data.name
                        else -> displayName
                    }
                }
                is ApiResult.Unauthorized -> logout()
                is ApiResult.Error -> Unit
            }
        }
    }

    private fun fetchMembership() {
        lifecycleScope.launch {
            isMembershipLoading = true
            when (val result = repository.getMyMembership()) {
                is ApiResult.Success -> {
                    membership = result.data
                }
                is ApiResult.Unauthorized -> logout()
                is ApiResult.Error -> {
                    if (result.code == 404) {
                        membership = null
                    }
                }
            }
            isMembershipLoading = false
        }
    }

    private fun fetchMembershipTypes() {
        lifecycleScope.launch {
            when (val result = repository.getMembershipTypes()) {
                is ApiResult.Success -> {
                    membershipTypes = result.data
                }
                else -> Unit
            }
        }
    }

    private fun fetchTrainerStatus() {
        lifecycleScope.launch {
            isTrainerLoading = true
            when (val meResult = repository.getMe()) {
                is ApiResult.Success -> {
                    val activeTrainerId = meResult.data.trainerId
                    val rawEndDate = meResult.data.trainerEndDate
                    currentTrainerId = activeTrainerId

                    if (activeTrainerId == null) {
                        currentTrainerId = null
                        trainerName = null
                        trainerStartDate = null
                        trainerEndDate = null
                    } else {
                        trainerEndDate = rawEndDate?.let(::formatIsoDateForDisplay)
                        trainerStartDate = rawEndDate?.let(::calculateTrainerStartDate)

                        when (val trainerResult = repository.getTrainerById(activeTrainerId)) {
                            is ApiResult.Success -> {
                                trainerName = "${trainerResult.data.firstName} ${trainerResult.data.lastName}".trim()
                            }
                            is ApiResult.Unauthorized -> {
                                logout()
                                return@launch
                            }
                            is ApiResult.Error -> {
                                trainerName = "Twój trener"
                            }
                        }
                    }
                }
                is ApiResult.Unauthorized -> {
                    logout()
                    return@launch
                }
                is ApiResult.Error -> {
                    currentTrainerId = null
                    trainerName = null
                    trainerStartDate = null
                    trainerEndDate = null
                }
            }
            isTrainerLoading = false
        }
    }

    private fun logout() {
        SessionManager.clearSession()
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    private fun navigateToMemberships() {
        val intent = Intent(this, MembershipsActivity::class.java)
        startActivity(intent)
    }

    private fun navigateToTrainers() {
        val intent = Intent(this, TrainersActivity::class.java)
        startActivity(intent)
    }

    private fun navigateToTrainerDetails(trainerId: Int) {
        val intent = Intent(this, TrainersActivity::class.java).apply {
            putExtra("OPEN_TRAINER_ID", trainerId)
        }
        startActivity(intent)
    }

    private fun navigateToProgress() {
        val intent = Intent(this, ProgressActivity::class.java)
        startActivity(intent)
    }

    private fun navigateToAccount() {
        val intent = Intent(this, AccountActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        }
        startActivity(intent)
    }
}

private fun displayNameFromEmail(email: String?): String {
    if (email.isNullOrBlank()) return "Użytkowniku"
    val localPart = email.substringBefore('@')
    if (!localPart.contains('.') && !localPart.contains('_')) {
        return "Użytkowniku"
    }
    val tokens = localPart
        .replace('_', ' ')
        .replace('.', ' ')
        .split(' ')
        .filter { it.isNotBlank() }

    if (tokens.isEmpty()) return email

    return tokens.joinToString(" ") { token ->
        token.lowercase().replaceFirstChar { char -> char.uppercase() }
    }
}

private fun formatIsoDateForDisplay(rawDate: String): String {
    val input = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val output = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    val parsed = runCatching { input.parse(rawDate) }.getOrNull() ?: return rawDate
    return output.format(parsed)
}

private fun calculateTrainerStartDate(rawEndDate: String): String {
    val input = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val output = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    val parsedEndDate = runCatching { input.parse(rawEndDate) }.getOrNull() ?: return "-"
    val calendar = Calendar.getInstance().apply {
        time = parsedEndDate
        add(Calendar.DAY_OF_YEAR, -TRAINER_RENTAL_DURATION_DAYS)
    }
    return output.format(calendar.time)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Navbar(onLogout: () -> Unit, balance: Double? = null, onBalanceClick: () -> Unit = {}, userRole: String? = null) {
    val isAdmin = userRole.equals("ADMIN", ignoreCase = true)
    val isTrainer = userRole.equals("TRAINER", ignoreCase = true)
    
    TopAppBar(
        title = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(end = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("FitManager", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                // show balance only for CLIENT role
                if (!isAdmin && !isTrainer) {
                    TextButton(onClick = onBalanceClick) {
                        val display = balance ?: SessionManager.getBalance()
                        Text("${String.format(java.util.Locale.getDefault(), "%.2f", display)} zł", fontSize = 16.sp)
                    }
                }
            }
        },
        actions = {
            TextButton(onClick = onLogout) {
                Text("Wyloguj")
            }
        }
    )
}

@Composable
fun MainContent(
    modifier: Modifier = Modifier, 
    displayName: String, 
    membership: MembershipResponse?, 
    isMembershipLoading: Boolean,
    membershipTypes: List<MembershipTypeResponse>,
    onBuyMembership: () -> Unit,
    trainerName: String?,
    trainerStartDate: String?,
    trainerEndDate: String?,
    isTrainerLoading: Boolean,
    onTrainerClick: () -> Unit,
    onNavigateToProgress: () -> Unit,
    assignedSessions: List<AssignedSessionDto>,
    onCompleteSession: (Int, CompleteSessionRequest) -> Unit = { _, _ -> }
) {
    val scrollState = rememberScrollState()
    var sessionToExecute by remember { mutableStateOf<AssignedSessionDto?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = "Mój panel", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(text = "Witaj $displayName!", fontSize = 16.sp)

        MembershipSection(
            membership = membership, 
            isMembershipLoading = isMembershipLoading,
            membershipTypes = membershipTypes,
            onBuyMembership = onBuyMembership
        )

        TrainerSection(
            trainerName = trainerName,
            trainerStartDate = trainerStartDate,
            trainerEndDate = trainerEndDate,
            isTrainerLoading = isTrainerLoading,
            onClick = onTrainerClick
        )

        val pendingSessions = assignedSessions.filter { it.status != "COMPLETED" }
        val completedSessions = assignedSessions.filter { it.status == "COMPLETED" }

        Text(text = "Zalecone treningi od trenera", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        if (pendingSessions.isNotEmpty()) {
            pendingSessions.forEach { session ->
                AssignedSessionCard(session = session, onExecute = { sessionToExecute = session })
            }
        } else {
            Text(text = "Nie masz aktualnie treningów do zrealizowania.", fontSize = 14.sp, color = Color.Gray)
        }

        if (completedSessions.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "Historia wykonanych treningów", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            completedSessions.forEach { session ->
                AssignedSessionCard(session = session, onExecute = { sessionToExecute = session })
            }
        }
        
        // Mały odstęp na samym dole
        Spacer(modifier = Modifier.height(8.dp))
    }

    if (sessionToExecute != null) {
        ExecuteSessionDialog(
            session = sessionToExecute!!,
            onDismiss = { sessionToExecute = null },
            onCompleteSession = onCompleteSession
        )
    }
}

@Composable
fun MembershipSection(
    membership: MembershipResponse?,
    isMembershipLoading: Boolean,
    membershipTypes: List<MembershipTypeResponse>,
    onBuyMembership: () -> Unit
) {

    val hasMembership = membership != null

    val backgroundColor = when {
        isMembershipLoading -> Color.LightGray.copy(alpha = 0.4f)
        hasMembership -> Color(0xFF4CAF50) // zielony
        else -> Color(0xFFF44336) // czerwony
    }

    val textColor = if (isMembershipLoading) Color.Black else Color.White

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            if (isMembershipLoading) {
                Text(
                    text = "Sprawdzam karnet...",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = textColor,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                CircularProgressIndicator()
            }

            else if (membership != null) {

                Text(
                    text = "Twój aktywny karnet",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Text(
                    "Typ: ${membership.membershipType.name}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = textColor
                )

                Text(
                    "Cena: ${membership.membershipType.price} zł",
                    fontSize = 16.sp,
                    color = textColor
                )

                Text(
                    "Wygasa: ${membership.endDate}",
                    fontSize = 16.sp,
                    color = textColor
                )
            }

            else {

                Text(
                    text = "Brak aktywnego karnetu",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    modifier = Modifier.padding(bottom = 8.dp)

                )

                if (membershipTypes.isNotEmpty()) {
                    Text(
                        "Dostępne opcje:",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = textColor
                    )

                    membershipTypes.forEach { type ->
                        Text(
                            "• ${type.name} - ${type.price} zł",
                            fontSize = 14.sp,
                            color = textColor
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }

                Button(
                    onClick = onBuyMembership,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Black,
                        contentColor = Color.White
                    )
                ) {
                    Text("Kup karnet")
                }
            }
        }
    }
}

@Composable
fun TrainerSection(
    trainerName: String?,
    trainerStartDate: String?,
    trainerEndDate: String?,
    isTrainerLoading: Boolean,
    onClick: () -> Unit
) {
    val hasTrainer = !trainerName.isNullOrBlank()

    val backgroundColor = when {
        isTrainerLoading -> Color.LightGray.copy(alpha = 0.4f)
        hasTrainer -> Color(0xFF4CAF50)
        else -> Color(0xFFF44336)
    }

    val textColor = if (isTrainerLoading) Color.Black else Color.White

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (hasTrainer) Modifier.clickable(onClick = onClick) else Modifier),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (isTrainerLoading) {
                Text(
                    text = "Sprawdzam trenera...",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = textColor,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                CircularProgressIndicator()
            } else if (hasTrainer) {
                Text(
                    text = "Twój trener personalny",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Text(
                    text = "Trener: $trainerName",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = textColor
                )

                Text(
                    text = "Od: ${trainerStartDate ?: "-"}",
                    fontSize = 16.sp,
                    color = textColor
                )

                Text(
                    text = "Do: ${trainerEndDate ?: "-"}",
                    fontSize = 16.sp,
                    color = textColor
                )
            } else {
                Text(
                    text = "Brak trenera personalnego",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
            }
        }
    }
}

@Composable
fun RowScope.TableCell(text: String, weight: Float, isHeader: Boolean = false) {
    Text(
        text = text,
        modifier = Modifier.weight(weight),
        fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
        fontSize = 14.sp
    )
}

@Composable
fun AssignedSessionCard(session: AssignedSessionDto, onExecute: () -> Unit) {
    val isCompleted = session.status == "COMPLETED"
    val containerColor = if (isCompleted) Color(0xFFF5F5F5) else Color.White
    val titleColor = if (isCompleted) Color.Gray else Green80
    val cardAlpha = if (isCompleted) 0.8f else 1f

    val context = LocalContext.current
    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) {
            generateSessionPdf(context, session, uri)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth().alpha(cardAlpha),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isCompleted) 0.dp else 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = session.title, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = titleColor)
                    Text(text = "Data wykonania: ${session.date} (${session.duration})", fontSize = 14.sp, color = Color.Gray)
                }
                TextButton(onClick = { pdfLauncher.launch("Plan_${session.title.replace(" ", "_")}.pdf") }) {
                    Text("Pobierz PDF", fontSize = 12.sp, color = Green80)
                }
            }
            if (session.exercises.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(8.dp))
                session.exercises.forEach { ex ->
                    val isTimeBased = ex.exerciseName.contains("Deska", ignoreCase = true) || ex.exerciseName.contains("Plank", ignoreCase = true)
                    val repsLabel = if (isTimeBased) "sek." else "powt."
                    val wLabel = if (ex.weight > 0) " | ${ex.weight}kg" else ""
                    Text("• ${ex.exerciseName}: ${ex.sets}x${ex.reps} $repsLabel$wLabel", fontSize = 14.sp)
                }
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Trener nie wprowadził jeszcze konkretnych ćwiczeń do tej sesji.", fontSize = 12.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, color = Color.Gray)
            }
            Spacer(modifier = Modifier.height(12.dp))
            if (isCompleted) {
                Text("✅ Trening został zakończony", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally))
            } else {
                Button(onClick = onExecute, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Green80)) {
                    Text("Rozpocznij i wpisz wyniki", color = Color.White)
                }
            }
        }
    }
}

data class SetEntry(val weight: String, val reps: String)

@Composable
fun ExecuteSessionDialog(
    session: AssignedSessionDto,
    onDismiss: () -> Unit,
    onCompleteSession: (Int, CompleteSessionRequest) -> Unit
) {
    var sessionLogs by remember {
        mutableStateOf(
            session.exercises.associate { ex ->
                ex.exerciseId to List(ex.sets) { SetEntry(weight = if (ex.weight > 0) ex.weight.toString() else "", reps = ex.reps.toString()) }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Zrealizuj: ${session.title}") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items(session.exercises) { ex ->
                    Column(modifier = Modifier.fillMaxWidth().background(Color(0xFFF5F5F5), RoundedCornerShape(8.dp)).padding(12.dp)) {
                        Text(ex.exerciseName, fontWeight = FontWeight.Bold)
                        val wPlan = if (ex.weight > 0) "${ex.weight}kg" else "-"
                        Text("Plan wg trenera: ${ex.sets}x${ex.reps} | $wPlan", fontSize = 12.sp, color = Color.Gray)
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        val logs = sessionLogs[ex.exerciseId] ?: emptyList()
                        logs.forEachIndexed { index, setEntry ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp), 
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(bottom = 4.dp)
                            ) {
                                Text("S. ${index + 1}", modifier = Modifier.width(36.dp), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                OutlinedTextField(
                                    value = setEntry.weight, 
                                    onValueChange = { newW -> 
                                        val newList = logs.toMutableList()
                                        newList[index] = setEntry.copy(weight = newW)
                                        sessionLogs = sessionLogs.toMutableMap().apply { put(ex.exerciseId, newList) }
                                    }, 
                                    label = { Text("Waga") }, 
                                    modifier = Modifier.weight(1f), 
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                                )
                                OutlinedTextField(
                                    value = setEntry.reps, 
                                    onValueChange = { newR -> 
                                        val newList = logs.toMutableList()
                                        newList[index] = setEntry.copy(reps = newR)
                                        sessionLogs = sessionLogs.toMutableMap().apply { put(ex.exerciseId, newList) }
                                    }, 
                                    label = { Text("Powt.") }, 
                                    modifier = Modifier.weight(1f), 
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalLogs = mutableListOf<SetLogDto>()
                    sessionLogs.forEach { (exId, sets) ->
                        sets.forEachIndexed { index, setEntry ->
                            val w = setEntry.weight.replace(",", ".").toDoubleOrNull() ?: 0.0
                            val r = setEntry.reps.toIntOrNull() ?: 0
                            finalLogs.add(SetLogDto(exId, index + 1, w, r))
                        }
                    }
                    onCompleteSession(session.id, CompleteSessionRequest(finalLogs))
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5))
            ) { Text("Zakończ trening", color = Color.White) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } }
    )
}

private fun generateSessionPdf(context: Context, session: AssignedSessionDto, uri: Uri) {
    try {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // Format A4
        val page = document.startPage(pageInfo)
        val canvas = page.canvas
        val paint = Paint()

        // Tło kartki (białe)
        paint.color = android.graphics.Color.WHITE
        canvas.drawRect(0f, 0f, 595f, 842f, paint)

        paint.color = android.graphics.Color.BLACK

        paint.textSize = 22f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("Plan Treningowy: ${session.title}", 50f, 60f, paint)

        paint.textSize = 14f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("Data: ${session.date} | Czas trwania: ${session.duration}", 50f, 100f, paint)
        canvas.drawText("Trener układający: ${session.trainerName}", 50f, 125f, paint)

        var y = 180f
        paint.textSize = 16f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("Zalecone ćwiczenia do wykonania:", 50f, y, paint)
        y += 30f

        paint.textSize = 14f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        if (session.exercises.isEmpty()) {
            canvas.drawText("Brak wprowadzonych ćwiczeń.", 50f, y, paint)
        } else {
            session.exercises.forEachIndexed { index, ex ->
                val isTimeBased = ex.exerciseName.contains("Deska", ignoreCase = true) || ex.exerciseName.contains("Plank", ignoreCase = true)
                val repsLabel = if (isTimeBased) "sek." else "powt."
                val wLabel = if (ex.weight > 0) " | ${ex.weight}kg" else ""
                val text = "${index + 1}. ${ex.exerciseName} - ${ex.sets} serie x ${ex.reps} $repsLabel$wLabel"
                
                canvas.drawText(text, 50f, y, paint)
                y += 25f
            }
        }

        document.finishPage(page)
        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            document.writeTo(outputStream)
        }
        document.close()
        Toast.makeText(context, "Zapisano plan do PDF!", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Wystąpił błąd podczas zapisu PDF.", Toast.LENGTH_SHORT).show()
    }
}

@Preview(showBackground = true)
@Composable
fun MainContentPreview() {
    GymManagerTheme {
        MainContent(
            displayName = "Jan Nowak", 
            membership = null, 
            isMembershipLoading = false,
            membershipTypes = emptyList(),
            onBuyMembership = {},
            trainerName = null,
            trainerStartDate = null,
            trainerEndDate = null,
            isTrainerLoading = false,
            onTrainerClick = {},
            onNavigateToProgress = {},
            assignedSessions = emptyList()
        )
    }
}