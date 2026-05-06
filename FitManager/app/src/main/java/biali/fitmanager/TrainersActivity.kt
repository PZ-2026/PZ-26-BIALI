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
import biali.fitmanager.ui.theme.Green80
import biali.fitmanager.ui.theme.GymManagerTheme
import androidx.compose.runtime.Composable
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class TrainersActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SessionManager.initialize(applicationContext)
        enableEdgeToEdge()

        val viewModel = ViewModelProvider(this)[TrainersViewModel::class.java]

        setContent {
            GymManagerTheme {
                val state by viewModel.state.collectAsState()

                LaunchedEffect(Unit) {
                    viewModel.fetchTrainers()
                }

                LaunchedEffect(state.sessionExpired) {
                    if (state.sessionExpired) {
                        logout()
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
                            onPickTrainer = { viewModel.pickTrainer(it) },
                            onResignTrainer = { viewModel.resignTrainer(it) },
                            currentTrainerId = state.currentTrainerId
                        )
                    } else {
                        TrainersListContent(
                            modifier = Modifier.padding(innerPadding),
                            trainers = state.trainers,
                            isLoading = state.isLoading,
                            error = state.error,
                            onTrainerClick = { viewModel.selectTrainer(it.id) },
                            onRefresh = { viewModel.fetchTrainers() },
                            currentTrainerId = state.currentTrainerId,
                            currentTrainer = state.currentTrainer,
                            currentTrainerEndDate = state.currentTrainerEndDate
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
                // Show trainer rental price (matches backend rental price)
                Text(text = "Cena wynajmu: 199.99 PLN / 30 dni", fontSize = 13.sp)
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
    onPickTrainer: (Int) -> Unit,
    onResignTrainer: (Int) -> Unit,
    currentTrainerId: Int?
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
                    text = "O trenerze:",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Doświadczony instruktor personalny, który pomoże Ci osiągnąć Twoje cele treningowe. Specjalizuje się w treningu siłowym i redukcji tkanki tłuszczowej.",
                    fontSize = 15.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = "Cena wynajmu: 199.99 PLN / 30 dni", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
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

        if (isLoading) {
            CircularProgressIndicator()
        } else {
            val isCurrentTrainer = trainer.id == currentTrainerId
            Button(
                onClick = { if (isCurrentTrainer) onResignTrainer(trainer.id) else onPickTrainer(trainer.id) },
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
