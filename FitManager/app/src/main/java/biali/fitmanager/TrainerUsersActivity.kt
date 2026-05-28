package biali.fitmanager

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
                            onNavigateToProgress = { navigateToProgress() }
                        )
                    }
                ) { innerPadding ->
                    TrainerUsersContent(
                        modifier = Modifier.padding(innerPadding),
                        state = state,
                        onRefreshClients = viewModel::refresh
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
                Text("230.00zł", fontSize = 16.sp)
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
    onRefreshClients: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = "Mój panel", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(text = "Panel podopiecznych trenera", fontSize = 16.sp)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .background(color = Color.LightGray)
                .padding(16.dp)
        ) {
            Text(text = "Grafik podopiecznych", modifier = Modifier.align(Alignment.Center))
        }

        Text(text = "Podopieczni", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)

        Button(
            onClick = onRefreshClients,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Green80, contentColor = Color.White)
        ) {
            Text("Odśwież podopiecznych")
        }

        if (state.isLoading) {
            Text("Ładowanie danych...")
        }

        state.error?.let { Text(it, fontWeight = FontWeight.SemiBold) }

        if (state.clients.isNotEmpty()) {
            Row(modifier = Modifier.fillMaxWidth()) {
                TrainerCell(text = "Imię i nazwisko", weight = 2f, isHeader = true)
                TrainerCell(text = "Email", weight = 2f, isHeader = true)
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), thickness = 1.dp, color = Color.LightGray)
        }

        state.clients.forEach { client ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                TrainerCell(text = "${client.firstName} ${client.lastName}", weight = 2f)
                TrainerCell(text = client.email, weight = 2f)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun RowScope.TrainerCell(text: String, weight: Float, isHeader: Boolean = false) {
    Text(
        text = text,
        modifier = Modifier.weight(weight),
        fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
        fontSize = 14.sp
    )
}

@Composable
private fun TrainerBottomNav(onNavigateToHome: () -> Unit = {}, onNavigateToProgress: () -> Unit = {}) {
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
            label = { Text("Trener") },
            icon = { androidx.compose.material3.Icon(Icons.Filled.Person, contentDescription = "Trener") },
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
            onClick = { },
            label = { Text("Konto") },
            icon = { androidx.compose.material3.Icon(Icons.Filled.Person, contentDescription = "Konto") }
        )
    }
}
