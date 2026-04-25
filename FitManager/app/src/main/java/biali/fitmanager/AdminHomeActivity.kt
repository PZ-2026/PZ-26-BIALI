package biali.fitmanager

import android.content.Intent
import android.widget.Toast
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.lifecycle.ViewModelProvider
import biali.fitmanager.network.SessionManager
import biali.fitmanager.ui.theme.Green80
import biali.fitmanager.ui.theme.GymManagerTheme
import biali.fitmanager.ui.theme.LightGreen80

class AdminHomeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        SessionManager.initialize(applicationContext)
        if (!hasAdminRole()) {
            Toast.makeText(this, "Brak uprawnień do panelu admina.", Toast.LENGTH_SHORT).show()
            logout()
            return
        }
        enableEdgeToEdge()
        val viewModel = ViewModelProvider(this)[AdminDashboardViewModel::class.java]
        setContent {
            GymManagerTheme {
                val state by viewModel.state.collectAsState()

                LaunchedEffect(state.sessionExpired) {
                    if (state.sessionExpired) {
                        logout()
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        AdminTopBar(
                            onLogout = ::logout
                        )
                    },
                    bottomBar = { AdminBottomNav() }
                ) { innerPadding ->
                    AdminDashboardScreen(
                        modifier = Modifier.padding(innerPadding),
                        state = state,
                        onRefresh = viewModel::refreshAll,
                        onUserFilterChange = viewModel::setUserFilter,
                        onUserFieldChange = viewModel::onFormChanged,
                        onSave = viewModel::saveForm,
                        onClear = viewModel::clearForm,
                        onEditUser = viewModel::fillFormFromUser,
                        onDeleteUser = viewModel::deleteUser,
                        onTrainerIdChange = viewModel::onTrainerIdChanged,
                        onClientIdChange = viewModel::onClientIdChanged,
                        onLoadTrainerClients = viewModel::loadTrainerClients,
                        onAssignClient = viewModel::assignClient,
                        onUnassignClient = viewModel::unassignClient
                    )
                }
            }
        }
    }

    private fun hasAdminRole(): Boolean {
        val role = SessionManager.getRole()
            ?: SessionManager.getToken()?.let(SessionManager::resolveRoleFromToken)
        return role.equals("ADMIN", ignoreCase = true)
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
private fun AdminTopBar(onLogout: () -> Unit) {
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
                Icon(Icons.Filled.ExitToApp, contentDescription = null)
                Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                Text("Wyloguj")
            }
        }
    )
}

@Composable
private fun AdminDashboardScreen(
    modifier: Modifier = Modifier,
    state: AdminDashboardUiState,
    onRefresh: () -> Unit,
    onUserFilterChange: (String) -> Unit,
    onUserFieldChange: ((AdminUserFormState) -> AdminUserFormState) -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit,
    onEditUser: (biali.fitmanager.network.UserResponse) -> Unit,
    onDeleteUser: (biali.fitmanager.network.UserResponse) -> Unit,
    onTrainerIdChange: (String) -> Unit,
    onClientIdChange: (String) -> Unit,
    onLoadTrainerClients: () -> Unit,
    onAssignClient: () -> Unit,
    onUnassignClient: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = "Mój panel", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(text = "Panel administratora", fontSize = 16.sp)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .background(color = Color.LightGray)
                .padding(16.dp)
        ) {
            Text(text = "Zarządzanie użytkownikami", modifier = Modifier.align(Alignment.Center))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onRefresh,
                colors = ButtonDefaults.buttonColors(containerColor = Green80, contentColor = Color.White)
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                Text("Odśwież")
            }
        }

        state.message?.let { Text(text = it, fontWeight = FontWeight.SemiBold) }
        state.error?.let { Text(text = it, fontWeight = FontWeight.SemiBold) }
        if (state.isLoading) {
            Text(text = "Ładowanie danych...")
        }

        AdminUsersSection(
            state = state,
            onUserFilterChange = onUserFilterChange,
            onEditUser = onEditUser,
            onDeleteUser = onDeleteUser
        )

        AdminUserFormSection(
            state = state,
            onUserFieldChange = onUserFieldChange,
            onSave = onSave,
            onClear = onClear
        )

        AdminTrainersSection(
            trainers = state.trainers,
            onEditUser = onEditUser,
            onDeleteUser = onDeleteUser
        )

        AdminTrainerClientsSection(
            state = state,
            onTrainerIdChange = onTrainerIdChange,
            onClientIdChange = onClientIdChange,
            onLoadTrainerClients = onLoadTrainerClients,
            onAssignClient = onAssignClient,
            onUnassignClient = onUnassignClient
        )
    }
}

@Composable
private fun AdminBottomNav() {
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
            onClick = { },
            label = { Text("Trener") },
            icon = { Icon(Icons.Filled.Person, contentDescription = "Trener") }
        )
        NavigationBarItem(
            selected = false,
            onClick = { },
            label = { Text("Postęp") },
            icon = { Icon(Icons.Filled.Edit, contentDescription = "Postęp") }
        )
        NavigationBarItem(
            selected = false,
            onClick = { },
            label = { Text("Konto") },
            icon = { Icon(Icons.Filled.Person, contentDescription = "Konto") }
        )
    }
}

@Composable
private fun AdminUsersSection(
    state: AdminDashboardUiState,
    onUserFilterChange: (String) -> Unit,
    onEditUser: (biali.fitmanager.network.UserResponse) -> Unit,
    onDeleteUser: (biali.fitmanager.network.UserResponse) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Użytkownicy", fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("ALL", "CLIENT", "TRAINER", "ADMIN").forEach { role ->
                    OutlinedButton(onClick = { onUserFilterChange(role) }) {
                        Text(role)
                    }
                }
            }
            HorizontalDivider()
            state.users.forEach { user ->
                UserRow(user = user, onEdit = { onEditUser(user) }, onDelete = { onDeleteUser(user) })
            }
        }
    }
}

@Composable
private fun AdminTrainersSection(
    trainers: List<biali.fitmanager.network.UserResponse>,
    onEditUser: (biali.fitmanager.network.UserResponse) -> Unit,
    onDeleteUser: (biali.fitmanager.network.UserResponse) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Trenerzy", fontWeight = FontWeight.Bold)
            trainers.forEach { trainer ->
                UserRow(user = trainer, onEdit = { onEditUser(trainer) }, onDelete = { onDeleteUser(trainer) })
            }
        }
    }
}

@Composable
private fun UserRow(
    user: biali.fitmanager.network.UserResponse,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("#${user.id} • ${user.firstName} ${user.lastName} • ${user.role}")
        Text(user.email)
        Text(user.phoneNumber ?: "Brak telefonu")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = null)
                Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                Text("Edytuj")
            }
            OutlinedButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = null)
                Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                Text("Usuń")
            }
        }
        HorizontalDivider()
    }
}

@Composable
private fun AdminUserFormSection(
    state: AdminDashboardUiState,
    onUserFieldChange: ((AdminUserFormState) -> AdminUserFormState) -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (state.form.id.isBlank()) "Dodaj / edytuj konto" else "Edycja konta #${state.form.id}", fontWeight = FontWeight.Bold)

            OutlinedTextField(
                value = state.form.email,
                onValueChange = { value -> onUserFieldChange { it.copy(email = value) } },
                label = { Text("Email") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = state.form.password,
                onValueChange = { value -> onUserFieldChange { it.copy(password = value) } },
                label = { Text("Hasło (opcjonalne przy edycji)") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = state.form.role,
                onValueChange = { value -> onUserFieldChange { it.copy(role = value.uppercase()) } },
                label = { Text("Rola: ADMIN / TRAINER / CLIENT") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = state.form.firstName,
                onValueChange = { value -> onUserFieldChange { it.copy(firstName = value) } },
                label = { Text("Imię") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = state.form.lastName,
                onValueChange = { value -> onUserFieldChange { it.copy(lastName = value) } },
                label = { Text("Nazwisko") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = state.form.phoneNumber,
                onValueChange = { value -> onUserFieldChange { it.copy(phoneNumber = value) } },
                label = { Text("Telefon") },
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onSave) { Text("Zapisz") }
                OutlinedButton(onClick = onClear) { Text("Wyczyść") }
            }
        }
    }
}

@Composable
private fun AdminTrainerClientsSection(
    state: AdminDashboardUiState,
    onTrainerIdChange: (String) -> Unit,
    onClientIdChange: (String) -> Unit,
    onLoadTrainerClients: () -> Unit,
    onAssignClient: () -> Unit,
    onUnassignClient: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Podopieczni trenera", fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = state.trainerIdForClients,
                onValueChange = onTrainerIdChange,
                label = { Text("trainerId") },
                modifier = Modifier.fillMaxWidth()
            )
            Button(onClick = onLoadTrainerClients) { Text("Pobierz klientów") }

            if (state.isTrainerClientsLoading) {
                Text("Ładowanie klientów...")
            }

            state.trainerClients.forEach { client ->
                Text("#${client.id} • ${client.firstName} ${client.lastName} • ${client.email}")
            }

            HorizontalDivider()

            OutlinedTextField(
                value = state.clientIdForAssignment,
                onValueChange = onClientIdChange,
                label = { Text("clientId") },
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAssignClient) { Text("Przypisz") }
                OutlinedButton(onClick = onUnassignClient) { Text("Odepnij") }
            }
        }
    }
}

