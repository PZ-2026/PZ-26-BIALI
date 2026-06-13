package biali.fitmanager

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import biali.fitmanager.network.AssignedSessionDto
import biali.fitmanager.network.SessionManager
import biali.fitmanager.network.UserResponse
import biali.fitmanager.network.MembershipTypeResponse
import kotlinx.coroutines.launch
import biali.fitmanager.ui.theme.Green80
import biali.fitmanager.ui.theme.GymManagerTheme
import androidx.compose.runtime.Composable

class TrainersActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SessionManager.initialize(applicationContext)
        enableEdgeToEdge()

        val viewModel = ViewModelProvider(this)[TrainersViewModel::class.java]
        val openTrainerId = intent.getIntExtra("OPEN_TRAINER_ID", -1).takeIf { it > 0 }

        setContent {
            GymManagerTheme {
                val state by viewModel.state.collectAsState()
                var selectedTrainerForPurchase by remember { mutableStateOf<UserResponse?>(null) }
                var trainerIdToResign by remember { mutableStateOf<Int?>(null) }
                var trainerEndDateForResign by remember { mutableStateOf<String?>(null) }
                val snackbarHostState = remember { SnackbarHostState() }
                val coroutineScope = rememberCoroutineScope()

                LaunchedEffect(Unit) {
                    viewModel.fetchTrainers()
                    openTrainerId?.let { viewModel.selectTrainer(it) }
                }

                LaunchedEffect(state.sessionExpired) {
                    if (state.sessionExpired) {
                        logout()
                    }
                }

                LaunchedEffect(state.actionSuccess) {
                    state.actionSuccess?.let { msg ->
                        if (msg == "Pomyślnie wybrano trenera!") {
                            val intent = Intent(this@TrainersActivity, HomeActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            }
                            startActivity(intent)
                            finish()
                        } else {
                            coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
                            viewModel.clearActionSuccess()
                        }
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        TrainersTopBar(
                            showBack = state.showDetails,
                            onBack = { viewModel.backToList() },
                            onClose = { finish() }
                        )
                    },
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                    bottomBar = {
                        FitBottomNav(
                            currentRoute = "trainers",
                            onNavigateToHome = { finish() },
                            onNavigateToTrainers = { /* Już tu jesteśmy */ },
                            onNavigateToMemberships = {
                                val intent = Intent(this@TrainersActivity, MembershipsActivity::class.java)
                                startActivity(intent)
                                finish()
                            },
                            onNavigateToProgress = {
                                val intent = Intent(this@TrainersActivity, ProgressActivity::class.java)
                                startActivity(intent)
                                finish()
                            },
                            onNavigateToAccount = {
                                val intent = Intent(this@TrainersActivity, AccountActivity::class.java)
                                startActivity(intent)
                                finish()
                            }
                        )
                    }
                ) { innerPadding ->
                    if (state.showDetails && state.selectedTrainer != null) {
                        TrainerDetailsContent(
                            modifier = Modifier.padding(innerPadding),
                            trainer = state.selectedTrainer!!,
                            isLoading = state.isLoading,
                            error = state.error,
                            successMessage = state.actionSuccess,
                            onPickTrainer = { selectedTrainerForPurchase = it },
                            onResignTrainer = { trainerId, endDate ->
                                trainerIdToResign = trainerId
                                trainerEndDateForResign = endDate
                            },
                            onSaveWorkoutResults = { sId, results -> viewModel.saveWorkoutResults(sId, results) },
                            currentTrainerId = state.currentTrainerId,
                            currentTrainerEndDate = state.currentTrainerEndDate,
                            currentTrainerSessions = state.currentTrainerSessions
                        )
                    } else {
                        TrainersListContent(
                            modifier = Modifier.padding(innerPadding),
                            trainers = state.trainers,
                            isLoading = state.isLoading,
                            error = state.error,
                            onTrainerClick = { trainer ->
                                if (trainer.id == state.currentTrainerId) {
                                    viewModel.selectTrainer(trainer.id)
                                } else {
                                    selectedTrainerForPurchase = trainer
                                }
                            },
                            onRefresh = { viewModel.fetchTrainers() },
                            currentTrainerId = state.currentTrainerId,
                            currentTrainer = state.currentTrainer,
                            currentTrainerEndDate = state.currentTrainerEndDate
                        )
                    }

                    trainerIdToResign?.let { trainerId ->
                        val remainingDays = trainerEndDateForResign?.let(::calculateRemainingDays) ?: 0
                        AlertDialog(
                            onDismissRequest = { trainerIdToResign = null; trainerEndDateForResign = null },
                            title = { Text("Potwierdzenie") },
                            text = { 
                                Text(
                                    "Czy na pewno chcesz zrezygnować z trenera?\n\n" +
                                    "Pozostało $remainingDays dni współpracy."
                                )
                            },
                            dismissButton = {
                                TextButton(onClick = { trainerIdToResign = null; trainerEndDateForResign = null }) {
                                    Text("Anuluj")
                                }
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        viewModel.resignTrainer(trainerId)
                                        trainerIdToResign = null
                                        trainerEndDateForResign = null
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                                ) {
                                    Text("Tak, rezygnuję", color = Color.White)
                                }
                            }
                        )
                    }

                    selectedTrainerForPurchase?.let { trainer ->
                        val purchaseType = MembershipTypeResponse(
                            id = trainer.id,
                            name = "Trener: ${trainer.firstName} ${trainer.lastName}",
                            price = TRAINER_RENTAL_PRICE,
                            durationDays = TRAINER_RENTAL_DURATION_DAYS
                        )

                        PurchaseConfirmationDialog(
                            type = purchaseType,
                            onDismiss = { selectedTrainerForPurchase = null },
                            onConfirm = {
                                // If user already has an active trainer, show message and don't proceed
                                if (state.currentTrainerId != null) {
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Masz już aktywnego trenera. Najpierw zrezygnuj.")
                                    }
                                    selectedTrainerForPurchase = null
                                } else {
                                    val balance = SessionManager.getBalance()
                                    if (balance < TRAINER_RENTAL_PRICE) {
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("Za mało środków")
                                        }
                                    } else {
                                        // trigger purchase; navigation will happen when viewModel updates actionSuccess
                                        viewModel.pickTrainer(trainer.id)
                                        selectedTrainerForPurchase = null
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Odśwież dane trenerów przy każdym wejściu do aktywności
        val viewModel = ViewModelProvider(this)[TrainersViewModel::class.java]
        viewModel.fetchTrainers()
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
fun TrainersTopBar(showBack: Boolean, onBack: () -> Unit, onClose: () -> Unit) {
    TopAppBar(
        title = { Text("Trenerzy") },
        navigationIcon = {
            IconButton(onClick = if (showBack) onBack else onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wstecz")
            }
        }
    )
}

@Composable
fun TrainersListContent(
    modifier: Modifier = Modifier,
    trainers: List<UserResponse>,
    isLoading: Boolean,
    error: String?,
    onTrainerClick: (UserResponse) -> Unit,
    onRefresh: () -> Unit,
    currentTrainerId: Int?,
    currentTrainer: UserResponse?,
    currentTrainerEndDate: String?
) {
    val formattedEndDate = currentTrainerEndDate?.let { formatDisplayDate(it) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FBF9)),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val title = if (currentTrainer != null) {
                    val endPart = formattedEndDate?.let { " do $it" } ?: ""
                    "Twoim trenerem jest ${currentTrainer.firstName} ${currentTrainer.lastName}$endPart r."
                } else {
                    "Wybierz swojego trenera"
                }

                Text(
                    text = title,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Green80
                )
                Text(
                    text = "Nowoczesna lista trenerów z szybkim wyborem i szczegółami.",
                    fontSize = 13.sp,
                    color = Color(0xFF546E7A)
                )
            }
        }

        if (isLoading && trainers.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            if (error != null) {
                Text(
                    text = error,
                    color = Color.Red,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Button(onClick = onRefresh) {
                    Text("Odśwież")
                }
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(trainers) { trainer ->
                    TrainerCard(
                        trainer = trainer,
                        onClick = { onTrainerClick(trainer) },
                        isSelected = trainer.id == currentTrainerId
                    )
                }
            }
        }
    }
}

@Composable
fun TrainerCard(trainer: UserResponse, onClick: () -> Unit, isSelected: Boolean = false) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFFEAF8F1) else Color.White,
            contentColor = if (isSelected) Color.White else Color.Black
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "${trainer.firstName} ${trainer.lastName}",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Green80
                )
                Text(text = trainer.email, fontSize = 14.sp, color = Color(0xFF546E7A))
                trainer.phoneNumber?.takeIf { it.isNotBlank() }?.let {
                    Text(text = "Telefon: $it", fontSize = 13.sp, color = Color(0xFF607D8B))
                }
                Text(
                    text = "Cena wynajmu: ${String.format(java.util.Locale.getDefault(), "%.2f", TRAINER_RENTAL_PRICE)} PLN / $TRAINER_RENTAL_DURATION_DAYS dni",
                    fontSize = 13.sp,
                    color = Color(0xFF607D8B)
                )
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = if (isSelected) Green80 else Color(0xFFEAF3FF)),
                shape = RoundedCornerShape(999.dp)
            ) {
                Text(
                    text = if (isSelected) "Wybrany" else "Szczegóły",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) Color.White else Color(0xFF1E88E5)
                )
            }
        }
    }
}

@Composable
fun TrainerDetailsContent(
    modifier: Modifier = Modifier,
    trainer: UserResponse,
    isLoading: Boolean,
    error: String?,
    successMessage: String?,
    onPickTrainer: (UserResponse) -> Unit,
    onResignTrainer: (Int, String?) -> Unit,
    onSaveWorkoutResults: (Int, List<WorkoutResultDraft>) -> Unit,
    currentTrainerId: Int?,
    currentTrainerEndDate: String?,
    currentTrainerSessions: List<AssignedSessionDto>
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FBF9)),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Szczegóły trenera",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Green80
                )
                Text(
                    text = "${trainer.firstName} ${trainer.lastName}",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = Green80
                )
                Text(text = "Email: ${trainer.email}", fontSize = 15.sp, color = Color(0xFF546E7A))
                trainer.phoneNumber?.takeIf { it.isNotBlank() }?.let {
                    Text(text = "Telefon: $it", fontSize = 15.sp, color = Color(0xFF607D8B))
                }
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF8F1)),
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Text(
                        text = "Cena wynajmu: ${String.format(java.util.Locale.getDefault(), "%.2f", TRAINER_RENTAL_PRICE)} PLN / $TRAINER_RENTAL_DURATION_DAYS dni",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Green80
                    )
                }
            }
        }

        if (successMessage != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF8F1)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    text = successMessage,
                    modifier = Modifier.padding(12.dp),
                    color = Green80,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (error != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    text = error,
                    modifier = Modifier.padding(12.dp),
                    color = Color(0xFFD32F2F),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        val isCurrentTrainer = trainer.id == currentTrainerId

        if (isCurrentTrainer) {
            TrainerWorkoutPlanSection(currentTrainerSessions, onSaveWorkoutResults)
        }

        if (isLoading) {
            CircularProgressIndicator()
        } else {
            Button(
                onClick = { 
                    if (isCurrentTrainer) {
                        onResignTrainer(trainer.id, currentTrainerEndDate)
                    } else {
                        onPickTrainer(trainer)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isCurrentTrainer) Color.Red else Green80
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    text = if (isCurrentTrainer) "Zrezygnuj z trenera" else "Wybierz tego trenera",
                    color = Color.White
                )
            }
        }
    }
}

class ExerciseInputState(initialSets: String, initialReps: String, initialWeight: String) {
    val sets = mutableStateOf(initialSets)
    val reps = mutableStateOf(initialReps)
    val weight = mutableStateOf(initialWeight)
}

@Composable
fun TrainerWorkoutPlanSection(sessions: List<AssignedSessionDto>, onSaveResults: (Int, List<WorkoutResultDraft>) -> Unit) {
    val sdf = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault())
    val today = sdf.format(java.util.Date())

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FBF9)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "Plan od trenera na dzień $today",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Green80
            )

            if (sessions.isEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = "Trener jeszcze nie rozpisał planu treningowego dla Twojego karnetu.",
                        fontSize = 14.sp,
                        color = Color(0xFF546E7A),
                        modifier = Modifier.padding(14.dp)
                    )
                }
            } else {
                sessions.forEach { session ->
                    val inputs = remember(session.id) { mutableMapOf<Int, ExerciseInputState>() }
                    
                    // Dynamicznie uzupełniamy stan wejść dla ćwiczeń, gdy dane wreszcie załadują się z backendu
                    session.exercises.forEach { ex ->
                        if (!inputs.containsKey(ex.exerciseId)) {
                            inputs[ex.exerciseId] = ExerciseInputState(ex.sets.toString(), ex.reps.toString(), ex.weight.toString())
                        }
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(14.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(text = session.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Green80)
                            Text(text = "Data: ${formatDisplayDate(session.date)} | Status: ${session.status}", fontSize = 13.sp, color = Color(0xFF607D8B))

                            if (session.exercises.isEmpty()) {
                                Text(
                                    text = "Brak dodanych ćwiczeń.",
                                    fontSize = 13.sp,
                                    color = Color(0xFF607D8B)
                                )
                            } else {
                                session.exercises.forEach { exercise ->
                                    val isTimeBased = exercise.exerciseName.contains("Deska", ignoreCase = true) || exercise.exerciseName.contains("Plank", ignoreCase = true)
                                    val repsLabel = if (isTimeBased) "sek." else "powt."

                                    val input = inputs[exercise.exerciseId]

                                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                        Text(text = "• ${exercise.exerciseName}", fontWeight = FontWeight.Bold, color = Color(0xFF37474F), fontSize = 14.sp)
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                                            OutlinedTextField(
                                                value = input?.sets?.value ?: "",
                                                onValueChange = { input?.sets?.value = it },
                                                label = { Text("Serie") },
                                                modifier = Modifier.weight(1f),
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                            )
                                            OutlinedTextField(
                                                value = input?.reps?.value ?: "",
                                                onValueChange = { input?.reps?.value = it },
                                                label = { Text(repsLabel) },
                                                modifier = Modifier.weight(1f),
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                            )
                                            OutlinedTextField(
                                                value = input?.weight?.value ?: "",
                                                onValueChange = { input?.weight?.value = it },
                                                label = { Text("Ciężar (kg)") },
                                                modifier = Modifier.weight(1f),
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                                            )
                                        }
                                    }
                                }
                                
                                if (session.status != "COMPLETED") {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Button(
                                        onClick = {
                                            val resultsToSave = session.exercises.map { ex ->
                                                val inp = inputs[ex.exerciseId]
                                                WorkoutResultDraft(
                                                    exerciseId = ex.exerciseId,
                                                    exerciseName = ex.exerciseName,
                                                    sets = inp?.sets?.value?.toIntOrNull() ?: ex.sets,
                                                    reps = inp?.reps?.value?.toIntOrNull() ?: ex.reps,
                                                    weight = inp?.weight?.value?.replace(",", ".")?.toDoubleOrNull() ?: ex.weight
                                                )
                                            }
                                            onSaveResults(session.id, resultsToSave)
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(containerColor = Green80)
                                    ) {
                                        Text("Zapisz wyniki treningu", color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun calculateRemainingDays(isoDateString: String): Long {
    return try {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        val endDate = sdf.parse(isoDateString) ?: return 0L
        val today = java.util.Date()
        val diff = endDate.time - today.time
        val days = java.util.concurrent.TimeUnit.MILLISECONDS.toDays(diff)
        maxOf(days, 0L)
    } catch (e: Exception) {
        0L
    }
}

/**
 * Formatuje datę do postaci DD.MM.YYYY używając wyłącznie manipulacji na stringach.
 * Obsługuje formaty: DD.MM.YYYY (zwraca bez zmian), YYYY-MM-DD (konwertuje),
 * oraz inne formaty zawierające 8 cyfr (próbuje wyodrębnić dzień/miesiąc/rok).
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