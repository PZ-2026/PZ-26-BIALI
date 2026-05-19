package biali.fitmanager

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import biali.fitmanager.network.SessionManager
import biali.fitmanager.network.UserResponse
import biali.fitmanager.network.MembershipTypeResponse
import kotlinx.coroutines.launch
import biali.fitmanager.ui.theme.Green80
import biali.fitmanager.ui.theme.GymManagerTheme
import androidx.compose.runtime.Composable
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.format.DateTimeFormatter

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

                // Navigate to Home when trainer purchase succeeds
                LaunchedEffect(state.actionSuccess) {
                    if (state.actionSuccess == "Pomyślnie wybrano trenera!") {
                        val intent = Intent(this@TrainersActivity, HomeActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                        startActivity(intent)
                        finish()
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
                        if (!state.showDetails) {
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
                                }
                            )
                        }
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
                            currentTrainerId = state.currentTrainerId,
                            currentTrainerEndDate = state.currentTrainerEndDate
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

@RequiresApi(Build.VERSION_CODES.O)
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
    // Formatter do docelowego formatu
    val outputFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    // Jeśli backend daje np. "2025-05-04" (ISO), używamy tego:
    val inputFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    val formattedEndDate = try {
        currentTrainerEndDate?.let {
            LocalDate.parse(it, inputFormatter).format(outputFormatter)
        }
    } catch (e: Exception) {
        null // fallback jeśli format się nie zgadza
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {

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
            modifier = Modifier.padding(bottom = 16.dp)
        )

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
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Green80 else Color.White,
            contentColor = if (isSelected) Color.White else Color.Black
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${trainer.firstName} ${trainer.lastName}",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(text = trainer.email, fontSize = 14.sp)
                Text(
                    text = "Cena wynajmu: ${String.format(java.util.Locale.getDefault(), "%.2f", TRAINER_RENTAL_PRICE)} PLN / $TRAINER_RENTAL_DURATION_DAYS dni",
                    fontSize = 13.sp
                )
            }
            Text("Szczegóły >", fontSize = 14.sp)
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
    currentTrainerId: Int?,
    currentTrainerEndDate: String?
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = "${trainer.firstName} ${trainer.lastName}",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "Email: ${trainer.email}", fontSize = 16.sp)
                trainer.phoneNumber?.let {
                    if (it.isNotBlank()) {
                        Text(text = "Telefon: $it", fontSize = 16.sp)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Cena wynajmu: ${String.format(java.util.Locale.getDefault(), "%.2f", TRAINER_RENTAL_PRICE)} PLN / $TRAINER_RENTAL_DURATION_DAYS dni",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (successMessage != null) {
            Text(text = successMessage, color = Green80, fontWeight = FontWeight.Bold)
        }

        if (error != null) {
            Text(text = error, color = Color.Red)
        }

        Spacer(modifier = Modifier.height(16.dp))

        val isCurrentTrainer = trainer.id == currentTrainerId

        if (isCurrentTrainer) {
            TrainerWorkoutPlanSection()
            Spacer(modifier = Modifier.height(16.dp))
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
                )
            ) {
                Text(
                    text = if (isCurrentTrainer) "Zrezygnuj z trenera" else "Wybierz tego trenera",
                    color = Color.White
                )
            }
        }
    }
}

@Composable
fun TrainerWorkoutPlanSection() {
    val today = LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Ćwiczenie na dzień $today",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            val exercises = listOf(
                Triple("Przysiady ze sztangą", "60 kg", "4 serie x 8 powtórzeń"),
                Triple("Wyciskanie sztangi leżąc", "70 kg", "4 serie x 6 powtórzeń"),
                Triple("Martwy ciąg", "100 kg", "5 serii x 5 powtórzeń"),
                Triple("Wiosłowanie hantlą", "30 kg", "4 serie x 10 powtórzeń"),
                Triple("Plank", "0 kg", "3 serie x 60 sekund")
            )

            exercises.forEach { (name, weight, details) ->
                Column(modifier = Modifier.padding(bottom = 10.dp)) {
                    Text(text = name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(text = "Ciężar: $weight", fontSize = 14.sp)
                    Text(text = "Serie i powtórzenia: $details", fontSize = 14.sp)
                }
            }
        }
    }
}

private fun calculateRemainingDays(isoDateString: String): Long {
    return try {
        val endDate = LocalDate.parse(isoDateString, DateTimeFormatter.ISO_LOCAL_DATE)
        val today = LocalDate.now()
        val days = ChronoUnit.DAYS.between(today, endDate)
        maxOf(days, 0L)
    } catch (e: Exception) {
        0L
    }
}
