package biali.fitmanager

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import biali.fitmanager.ui.theme.GymManagerTheme
import androidx.lifecycle.ViewModelProvider
import biali.fitmanager.ui.theme.Green80
import biali.fitmanager.ui.theme.LightGreen80
import biali.fitmanager.network.SessionManager
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val trainerSessionDateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

private data class TrainerClientSummary(
    val fullName: String,
    val email: String,
    val clientId: Int,
    val nextTrainingDate: LocalDate?,
    val nextTrainingStatus: String,
    val trainingInfoText: String,
    val hasUpcomingTraining: Boolean,
    val totalSessions: Int
)

class TrainerUsersActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        SessionManager.initialize(applicationContext)
        enableEdgeToEdge()
        val viewModel = ViewModelProvider(this)[TrainerUsersViewModel::class.java]
        setContent {
            GymManagerTheme {
                val state by viewModel.state.collectAsState()

                LaunchedEffect(Unit) {
                    viewModel.initialize()
                }

                LaunchedEffect(state.sessionExpired) {
                    if (state.sessionExpired) {
                        logout()
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = { TrainerNavbar(onLogout = ::logout) },
                    bottomBar = {
                        TrainerBottomNav(
                            onNavigateToHome = ::navigateToHome,
                            onNavigateToProgress = { navigateToProgress() },
                            onNavigateToAccount = { navigateToAccount() }
                        )
                    }
                ) { innerPadding ->
                    TrainerUsersContent(
                        modifier = Modifier.padding(innerPadding),
                        state = state,
                        onPlanTraining = ::navigateToPlanTraining
                    )
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

    private fun navigateToHome() {
        val intent = Intent(this, TrainerHomeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        }
        startActivity(intent)
    }

    private fun navigateToProgress() {
        val intent = Intent(this, TrainerProgressActivity::class.java).apply {
        }
        startActivity(intent)
    }

    private fun navigateToPlanTraining(clientId: Int, clientName: String) {
        val intent = Intent(this, TrainerProgressActivity::class.java).apply {
            putExtra(EXTRA_PLAN_CLIENT_ID, clientId)
            putExtra(EXTRA_PLAN_CLIENT_NAME, clientName)
        }
        startActivity(intent)
    }

    private fun navigateToAccount() {
        val intent = Intent(this, AccountActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        }
        startActivity(intent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainerNavbar(onLogout: () -> Unit) {
    TopAppBar(
        title = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("FitManager", fontSize = 16.sp, fontWeight = FontWeight.Bold)
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
fun TrainerUsersContent(
    modifier: Modifier = Modifier,
    state: TrainerUsersUiState,
    onPlanTraining: (Int, String) -> Unit = { _, _ -> }
) {
    val scrollState = rememberScrollState()
    val today = LocalDate.now()
    val clientSummaries = state.clients.map { client ->
        buildTrainerClientSummary(client, state.trainingSessions, today)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = "Podopieczni", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)

        if (state.isLoading) {
            Text("Ładowanie danych...")
        }

        state.error?.let { Text(it, fontWeight = FontWeight.SemiBold) }

        if (clientSummaries.isEmpty() && !state.isLoading) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(text = "Brak podopiecznych", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Gdy przypiszesz treningi, pojawią się tutaj z datą i statusem.",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                }
            }
        }

        clientSummaries.forEach { summary ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPlanTraining(summary.clientId, summary.fullName) },
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, Color(0xFFE4E8EE)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = summary.fullName, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text(text = summary.email, fontSize = 13.sp, color = Color.Gray)
                        }

                        TrainingBadge(
                            text = if (summary.hasUpcomingTraining) "Zapisany" else "Brak",
                            backgroundColor = if (summary.hasUpcomingTraining) LightGreen80 else Color(0xFFFFE0E0),
                            contentColor = if (summary.hasUpcomingTraining) Green80 else Color(0xFFC62828)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(text = "Następny trening", fontSize = 12.sp, color = Color.Gray)
                    Text(
                        text = summary.trainingInfoText,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (summary.hasUpcomingTraining) Green80 else Color(0xFFC62828)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = summary.nextTrainingStatus,
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Wszystkich sesji: ${summary.totalSessions}",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

private fun buildTrainerClientSummary(
    client: biali.fitmanager.network.UserResponse,
    sessions: List<ClientTrainingSession>,
    today: LocalDate
): TrainerClientSummary {
    val fullName = "${client.firstName} ${client.lastName}".trim()
    val clientSessions = sessions.filter { session ->
        session.clientName?.trim()?.equals(fullName, ignoreCase = true) == true
    }

    val datedSessions = clientSessions.mapNotNull { session ->
        runCatching { LocalDate.parse(session.date, trainerSessionDateFormatter) }
            .getOrNull()
            ?.let { parsedDate -> parsedDate to session }
    }

    val upcomingSession = datedSessions
        .filter { (date, _) -> !date.isBefore(today) }
        .minByOrNull { (date, _) -> date }

    val nextTrainingDate = upcomingSession?.first
    val nextTrainingStatus = when {
        upcomingSession == null && clientSessions.isEmpty() -> "Brak zapisanych treningów"
        upcomingSession == null -> "Najbliższy termin minął"
        upcomingSession.first == today -> "Trening zaplanowany na dzisiaj"
        else -> "Trening zaplanowany na ${upcomingSession.first.format(trainerSessionDateFormatter)}"
    }

    val trainingInfoText = nextTrainingDate?.format(trainerSessionDateFormatter) ?: "Brak"

    return TrainerClientSummary(
        fullName = fullName,
        email = client.email,
        clientId = client.id,
        nextTrainingDate = nextTrainingDate,
        nextTrainingStatus = nextTrainingStatus,
        trainingInfoText = trainingInfoText,
        hasUpcomingTraining = nextTrainingDate != null,
        totalSessions = clientSessions.size
    )
}

@Composable
private fun TrainingBadge(text: String, backgroundColor: Color, contentColor: Color) {
    Card(
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        shape = RoundedCornerShape(999.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = contentColor
        )
    }
}

@Composable
private fun TrainerBottomNav(onNavigateToHome: () -> Unit = {}, onNavigateToProgress: () -> Unit = {}, onNavigateToAccount: () -> Unit = {}) {
    NavigationBar(
        containerColor = Color.White,
        tonalElevation = 8.dp
    ) {
        NavigationBarItem(
            selected = false,
            onClick = onNavigateToHome,
            label = { Text("Panel") },
            icon = { androidx.compose.material3.Icon(Icons.Filled.Home, contentDescription = "Panel") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Green80,
                selectedTextColor = Green80,
                indicatorColor = LightGreen80
            )
        )
        NavigationBarItem(
            selected = true,
            onClick = { },
            label = { Text("Podopieczni") },
            icon = { androidx.compose.material3.Icon(Icons.Filled.Person, contentDescription = "Podopieczni") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Green80,
                selectedTextColor = Green80,
                indicatorColor = LightGreen80
            )
        )
        NavigationBarItem(
            selected = false,
            onClick = onNavigateToProgress,
            label = { Text("Postęp") },
            icon = { androidx.compose.material3.Icon(Icons.Filled.Edit, contentDescription = "Postęp") }
        )
        NavigationBarItem(
            selected = false,
            onClick = onNavigateToAccount,
            label = { Text("Konto") },
            icon = { androidx.compose.material3.Icon(Icons.Filled.AccountCircle, contentDescription = "Konto") }
        )
    }
}
