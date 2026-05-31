package biali.fitmanager

import android.content.Intent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import biali.fitmanager.network.SessionManager
import biali.fitmanager.ui.theme.Green80
import biali.fitmanager.ui.theme.GymManagerTheme
import biali.fitmanager.ui.theme.LightGreen80

class TrainerHomeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        SessionManager.initialize(applicationContext)

        if (!hasTrainerRole()) {
            Toast.makeText(this, "Brak uprawnień do panelu trenera.", Toast.LENGTH_SHORT).show()
            logout()
            return
        }

        enableEdgeToEdge()
        val viewModel = ViewModelProvider(this)[TrainerHomeViewModel::class.java]

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
                    topBar = {
                        TrainerHomeTopBar(onLogout = ::logout)
                    },
                    bottomBar = {
                        TrainerHomeBottomNav(
                            onNavigateToClients = ::navigateToClients,
                            onNavigateToProgress = ::navigateToProgress,
                            onNavigateToAccount = ::navigateToAccount
                        )
                    }
                ) { innerPadding ->
                    TrainerHomeContent(
                        modifier = Modifier.padding(innerPadding),
                        state = state,
                        onNavigateToClients = ::navigateToClients,
                        onNavigateToProgress = ::navigateToProgress
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh data when returning to this activity
        val viewModel = ViewModelProvider(this)[TrainerHomeViewModel::class.java]
        viewModel.refresh()
    }

    private fun hasTrainerRole(): Boolean {
        val role = SessionManager.getRole()
            ?: SessionManager.getToken()?.let(SessionManager::resolveRoleFromToken)
        return role.equals("TRAINER", ignoreCase = true)
    }

    private fun logout() {
        SessionManager.clearSession()
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    private fun navigateToClients() {
        val intent = Intent(this, TrainerUsersActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        }
        startActivity(intent)
    }

    private fun navigateToProgress() {
        val intent = Intent(this, TrainerProgressActivity::class.java).apply {
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrainerHomeTopBar(onLogout: () -> Unit) {
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
                Icon(Icons.Filled.ExitToApp, contentDescription = null)
                Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                Text("Wyloguj")
            }
        }
    )
}

@Composable
private fun TrainerHomeContent(
    modifier: Modifier = Modifier,
    state: TrainerHomeUiState,
    onNavigateToClients: () -> Unit,
    onNavigateToProgress: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = "Witaj ${state.displayName}!", fontSize = 16.sp)

        // Messages
        state.error?.let { Text(text = it, fontWeight = FontWeight.SemiBold) }
        if (state.isLoading) {
            Text(text = "Ładowanie danych...")
        }

        // Stats overview row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                label = "Podopieczni",
                value = state.clientsCount.toString(),
                color = Green80,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                label = "Treningi",
                value = state.totalSessions.toString(),
                color = Color(0xFF1E88E5),
                modifier = Modifier.weight(1f)
            )
            StatCard(
                label = "Pomiary",
                value = state.progressLogsCount.toString(),
                color = Color(0xFFFF9800),
                modifier = Modifier.weight(1f)
            )
        }

        // Welcome card with quick info
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = LightGreen80),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Panel trenera",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2E7D32)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Zarządzaj swoimi podopiecznymi, planuj treningi i śledź postępy.",
                    fontSize = 14.sp,
                    color = Color(0xFF33691E)
                )
            }
        }

        // Sessions status cards
        Text(text = "Status treningów", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)

        if (state.totalSessions == 0 && !state.isLoading) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Brak treningów. Zaplanuj pierwszy trening dla swoich podopiecznych.",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SessionStatusCard(
                    label = "Szkice",
                    value = state.draftSessions.toString(),
                    color = Color(0xFFFFA000),
                    modifier = Modifier.weight(1f)
                )
                SessionStatusCard(
                    label = "Oczekujące",
                    value = state.confirmedSessions.toString(),
                    color = Color(0xFF1E88E5),
                    modifier = Modifier.weight(1f)
                )
                SessionStatusCard(
                    label = "Zakończone",
                    value = state.completedSessions.toString(),
                    color = Color(0xFF4CAF50),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Quick action cards
        Text(text = "Szybkie akcje", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)

        QuickActionCard(
            icon = Icons.Filled.Person,
            title = "Moi podopieczni",
            description = "Przeglądaj listę swoich klientów (${state.clientsCount})",
            buttonText = "Otwórz",
            onClick = onNavigateToClients
        )

        QuickActionCard(
            icon = Icons.Filled.Edit,
            title = "Postęp i treningi",
            description = "Zarządzaj pomiarami, planuj treningi i śledź postępy",
            buttonText = "Otwórz",
            onClick = onNavigateToProgress
        )

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                color = color
            )
            Text(
                text = label,
                fontSize = 12.sp,
                color = Color.Gray
            )
        }
    }
}

@Composable
private fun SessionStatusCard(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = color),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )
            Text(
                text = label,
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.9f)
            )
        }
    }
}

@Composable
private fun QuickActionCard(
    icon: ImageVector,
    title: String,
    description: String,
    buttonText: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = LightGreen80,
                        shape = RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Green80,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    text = description,
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Green80,
                    contentColor = Color.White
                )
            ) {
                Text(buttonText, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun TrainerHomeBottomNav(
    onNavigateToClients: () -> Unit = {},
    onNavigateToProgress: () -> Unit = {},
    onNavigateToAccount: () -> Unit = {}
) {
    NavigationBar(
        containerColor = Color.White,
        tonalElevation = 8.dp
    ) {
        NavigationBarItem(
            selected = true,
            onClick = { },
            label = { Text("Panel") },
            icon = { Icon(Icons.Filled.Home, contentDescription = "Panel") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Green80,
                selectedTextColor = Green80,
                indicatorColor = LightGreen80
            )
        )
        NavigationBarItem(
            selected = false,
            onClick = onNavigateToClients,
            label = { Text("Podopieczni") },
            icon = { Icon(Icons.Filled.Person, contentDescription = "Podopieczni") }
        )
        NavigationBarItem(
            selected = false,
            onClick = onNavigateToProgress,
            label = { Text("Postęp") },
            icon = { Icon(Icons.Filled.Edit, contentDescription = "Postęp") }
        )
        NavigationBarItem(
            selected = false,
            onClick = onNavigateToAccount,
            label = { Text("Konto") },
            icon = { Icon(Icons.Filled.Person, contentDescription = "Konto") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Green80,
                selectedTextColor = Green80,
                indicatorColor = LightGreen80
            )
        )
    }
}
