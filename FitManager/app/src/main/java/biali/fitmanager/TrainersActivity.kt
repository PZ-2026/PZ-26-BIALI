package biali.fitmanager

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
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
import biali.fitmanager.ui.theme.LightGreen80

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
                            onPickTrainer = { viewModel.pickTrainer(it) }
                        )
                    } else {
                        TrainersListContent(
                            modifier = Modifier.padding(innerPadding),
                            trainers = state.trainers,
                            isLoading = state.isLoading,
                            error = state.error,
                            onTrainerClick = { viewModel.selectTrainer(it.id) },
                            onRefresh = { viewModel.fetchTrainers() }
                        )
                    }
                }
            }
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
    onRefresh: () -> Unit
) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "Wybierz swojego trenera",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (isLoading && trainers.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            if (error != null) {
                Text(text = error, color = Color.Red, modifier = Modifier.padding(bottom = 8.dp))
                Button(onClick = onRefresh) { Text("Odśwież") }
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(trainers) { trainer ->
                    TrainerCard(trainer = trainer, onClick = { onTrainerClick(trainer) })
                }
            }
        }
    }
}

@Composable
fun TrainerCard(trainer: UserResponse, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                Text(text = trainer.email, fontSize = 14.sp, color = Color.Gray)
            }
            Text("Szczegóły >", fontSize = 14.sp, color = Green80)
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
    onPickTrainer: (Int) -> Unit
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
            Button(
                onClick = { onPickTrainer(trainer.id) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Green80)
            ) {
                Text("Wybierz tego trenera", color = Color.White)
            }
        }
    }
}
