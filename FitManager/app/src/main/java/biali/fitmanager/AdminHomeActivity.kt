package biali.fitmanager

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlinx.coroutines.launch
import androidx.core.content.FileProvider
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

        // Safe edge-to-edge: Samsung devices (e.g. SM-G781B) may crash/minimize
        // when enableEdgeToEdge() is called. Wrap in try-catch.
        try {
            enableEdgeToEdge()
        } catch (_: Exception) {
            // Samsung-specific bug: silently ignore
        }

        val viewModel = ViewModelProvider(this)[AdminDashboardViewModel::class.java]

        // Wrap the entire Compose content setup in a try-catch to prevent
        // the app from minimizing if any exception occurs during rendering.
        try {
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
                    bottomBar = {
                        AdminBottomNav(
                            currentRoute = "panel",
                            onNavigateToPanel = { },
                            onNavigateToAccount = ::navigateToAccount
                        )
                    }
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
                        onUnassignClient = viewModel::unassignClient,
                        onMembershipTypeFieldChange = viewModel::onMembershipTypeFieldChange,
                        onMembershipTypeSave = viewModel::saveMembershipTypeForm,
                        onMembershipTypeClear = viewModel::clearMembershipTypeForm,
                        onEditMembershipType = viewModel::fillMembershipTypeForm,
                        onDeleteMembershipType = viewModel::deleteMembershipType,
                                                            onGenerateReport = {
                                                                this@AdminHomeActivity.generateUsersReport()
                                                            }
                        ) // end AdminDashboardScreen call
                    } // end Scaffold lambda
                } // end GymManagerTheme
            } // end setContent
        } catch (e: Exception) {
            // If anything crashes during Compose setup, show a Toast instead of minimizing
            android.util.Log.e("AdminHomeActivity", "Error setting up admin panel", e)
            Toast.makeText(this, "Błąd inicjalizacji panelu: ${e.message}", Toast.LENGTH_LONG).show()
            logout()
        }
    }

    // Helper to download users report PDF and open it.
    private fun generateUsersReport() {
        val repo = biali.fitmanager.network.FitManagerRepository()
        this.lifecycleScope.launch {
            when (val res = repo.downloadUsersReportPdf()) {
                is biali.fitmanager.network.ApiResult.Success -> {
                    val body = res.data
                    try {
                        val cacheFile = java.io.File(this@AdminHomeActivity.cacheDir, "users-report.pdf")
                        body.byteStream().use { input ->
                            cacheFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }

                        val uri = FileProvider.getUriForFile(this@AdminHomeActivity, adminHomePackageName(), cacheFile)
                        val intent = Intent(Intent.ACTION_VIEW)
                        intent.setDataAndType(uri, "application/pdf")
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                        this@AdminHomeActivity.startActivity(intent)
                    } catch (ex: Exception) {
                        Toast.makeText(this@AdminHomeActivity, "Błąd zapisu/pliku: ${ex.message}", Toast.LENGTH_LONG).show()
                    }
                }
                is biali.fitmanager.network.ApiResult.Unauthorized -> {
                    Toast.makeText(this@AdminHomeActivity, "Brak uprawnień.", Toast.LENGTH_SHORT).show()
                }
                is biali.fitmanager.network.ApiResult.Error -> {
                    Toast.makeText(this@AdminHomeActivity, res.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Return packageName.provider authorities string for FileProvider
    private fun adminHomePackageName(): String = applicationContext.packageName + ".provider"

    private fun hasAdminRole(): Boolean {
        val role = SessionManager.getRole()
            ?: SessionManager.getToken()?.let(SessionManager::resolveRoleFromToken)
        return role.equals("ADMIN", ignoreCase = true)
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = null,
                        tint = Green80,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("FitManager", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        },
        actions = {
            TextButton(onClick = onLogout) {
                Icon(Icons.Filled.ExitToApp, contentDescription = null, tint = Color(0xFF666666))
                Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                Text("Wyloguj", color = Color(0xFF666666))
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.White,
            titleContentColor = Color(0xFF1B5E20)
        )
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
    onGenerateReport: () -> Unit,
    onTrainerIdChange: (String) -> Unit,
    onClientIdChange: (String) -> Unit,
    onLoadTrainerClients: () -> Unit,
    onAssignClient: () -> Unit,
    onUnassignClient: () -> Unit,
    onMembershipTypeFieldChange: ((AdminMembershipTypeFormState) -> AdminMembershipTypeFormState) -> Unit,
    onMembershipTypeSave: () -> Unit,
    onMembershipTypeClear: () -> Unit,
    onEditMembershipType: (biali.fitmanager.network.MembershipTypeResponse) -> Unit,
    onDeleteMembershipType: (Int) -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text = "Panel administratora", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(text = "Nowoczesny podgląd wszystkich narzędzi administracyjnych", fontSize = 15.sp, color = Color.Gray)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DashboardTile(
                title = "Użytkownicy",
                description = "Lista kont i role",
                count = state.users.size.toString(),
                background = Color(0xFFEAF8F1),
                accent = Green80,
                modifier = Modifier.weight(1f)
            )
            DashboardTile(
                title = "Trenerzy",
                description = "Zespół trenerski",
                count = state.trainers.size.toString(),
                background = Color(0xFFEAF3FF),
                accent = Color(0xFF1E88E5),
                modifier = Modifier.weight(1f)
            )
            DashboardTile(
                title = "Karnety",
                description = "Typy i ceny",
                count = state.membershipTypes.size.toString(),
                background = Color(0xFFFFF4E1),
                accent = Color(0xFFFF9800),
                modifier = Modifier.weight(1f)
            )
        }

        state.message?.let {
            Card(
                colors = CardDefaults.cardColors(containerColor = LightGreen80),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = it,
                    modifier = Modifier.padding(12.dp),
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF2E7D32)
                )
            }
        }
        state.error?.let {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = it,
                    modifier = Modifier.padding(12.dp),
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFC62828)
                )
            }
        }
        if (state.isLoading) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = Green80
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Ładowanie danych...", color = Color(0xFF666666))
                }
            }
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

        AdminReportSection(onGenerateReport = onGenerateReport)

        AdminTrainersSection(
            trainers = state.trainers,
            onEditUser = onEditUser,
            onDeleteUser = onDeleteUser
        )

        AdminMembershipTypesSection(
            state = state,
            onMembershipTypeFieldChange = onMembershipTypeFieldChange,
            onSave = onMembershipTypeSave,
            onClear = onMembershipTypeClear,
            onEditMembershipType = onEditMembershipType,
            onDeleteMembershipType = onDeleteMembershipType
        )

        AdminTrainerClientsSection(
            state = state,
            onTrainerIdChange = onTrainerIdChange,
            onClientIdChange = onClientIdChange,
            onLoadTrainerClients = onLoadTrainerClients,
            onAssignClient = onAssignClient,
            onUnassignClient = onUnassignClient
        )

        // Footer
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "FitManager v1.0 © 2025",
            fontSize = 12.sp,
            color = Color(0xFF999999),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun GymInfoCard() {
    val context = androidx.compose.ui.platform.LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = null,
                    tint = Green80,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "O siłowni",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1B5E20)
                )
            }

            HorizontalDivider(color = Color(0xFFE0E0E0))

            InfoRow(
                icon = Icons.Filled.LocationOn,
                label = "Adres",
                value = "ul. Sportowa 42, 00-001 Warszawa"
            )
            InfoRow(
                icon = Icons.Filled.Call,
                label = "Telefon",
                value = "+48 123 456 789"
            )
            InfoRow(
                icon = Icons.Filled.Email,
                label = "E-mail",
                value = "kontakt@biali-fitmanager.pl"
            )
            InfoRow(
                icon = Icons.Filled.DateRange,
                label = "Godziny otwarcia",
                value = "Pon-Pt: 06:00-23:00\nSob-Nd: 08:00-20:00"
            )

            HorizontalDivider(color = Color(0xFFE0E0E0))

            // Clickable map link
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFF5F5F5))
                    .clickable {
                        openMap(context)
                    }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.LocationOn,
                    contentDescription = null,
                    tint = Green80,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Zobacz na mapie",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Green80
                )
            }
        }
    }
}

private fun openMap(context: android.content.Context) {
    val gmmIntentUri = Uri.parse("geo:0,0?q=Siłownia+FitManager+Warszawa")
    val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
        setPackage("com.google.android.apps.maps")
    }
    try {
        context.startActivity(mapIntent)
    } catch (_: Exception) {
        val webIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://maps.google.com/?q=Siłownia+Warszawa")
        )
        context.startActivity(webIntent)
    }
}

@Composable
private fun InfoRow(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = Color(0xFF666666),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = label,
                fontSize = 12.sp,
                color = Color(0xFF999999)
            )
            Text(
                text = value,
                fontSize = 14.sp,
                color = Color(0xFF333333)
            )
        }
    }
}

@Composable
private fun DashboardTile(
    title: String,
    description: String,
    count: String,
    background: Color,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.height(136.dp),
        colors = CardDefaults.cardColors(containerColor = background),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = accent)
            Text(text = count, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = accent)
            Text(text = description, fontSize = 11.sp, color = Color(0xFF546E7A))
        }
    }
}

@Composable
private fun AdminUsersSection(
    state: AdminDashboardUiState,
    onUserFilterChange: (String) -> Unit,
    onEditUser: (biali.fitmanager.network.UserResponse) -> Unit,
    onDeleteUser: (biali.fitmanager.network.UserResponse) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FBF9)),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Użytkownicy", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Green80)
                Text("Filtruj i zarządzaj kontami", fontSize = 12.sp, color = Color.Gray)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf("ALL", "CLIENT").forEach { role ->
                        val buttonColors = when (role) {
                            "ALL" -> ButtonDefaults.buttonColors(containerColor = Color(0xFFECEFF1), contentColor = Color(0xFF37474F))
                            else -> ButtonDefaults.buttonColors(containerColor = Color(0xFFEAF8F1), contentColor = Green80)
                        }
                        Button(
                            onClick = { onUserFilterChange(role) },
                            colors = buttonColors,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(role)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf("TRAINER", "ADMIN").forEach { role ->
                        val buttonColors = when (role) {
                            "TRAINER" -> ButtonDefaults.buttonColors(containerColor = Color(0xFFEAF3FF), contentColor = Color(0xFF1E88E5))
                            else -> ButtonDefaults.buttonColors(containerColor = Color(0xFFFFEBEE), contentColor = Color(0xFFD32F2F))
                        }
                        Button(
                            onClick = { onUserFilterChange(role) },
                            colors = buttonColors,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(role)
                        }
                    }
                }
            }
            HorizontalDivider(color = Color(0xFFE0E0E0))
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF3F8FF)),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Trenerzy", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF1E88E5))
            trainers.forEach { trainer ->
                UserRow(user = trainer, onEdit = { onEditUser(trainer) }, onDelete = { onDeleteUser(trainer) })
            }
        }
    }
}

@Composable
private fun AdminReportSection(onGenerateReport: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFDFBF5)),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Raporty", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFFB26A00))
                Button(onClick = onGenerateReport, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB26A00), contentColor = Color.White)) {
                    Icon(Icons.Filled.Person, contentDescription = null)
                    Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                    Text("Generuj raport")
                }
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(14.dp)) {
            Text("#${user.id} • ${user.firstName} ${user.lastName} • ${user.role}", fontWeight = FontWeight.SemiBold)
            Text(user.email, color = Color(0xFF546E7A))
            Text(user.phoneNumber ?: "Brak telefonu", color = Color.Gray)
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
        }
    }
}

@Composable
private fun AdminUserFormSection(
    state: AdminDashboardUiState,
    onUserFieldChange: ((AdminUserFormState) -> AdminUserFormState) -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FBF9)),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (state.form.id.isBlank()) "Dodaj / edytuj konto" else "Edycja konta #${state.form.id}", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Green80)

            OutlinedTextField(
                value = state.form.email,
                onValueChange = { value -> onUserFieldChange { it.copy(email = value) } },
                label = { Text("Email") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            )
            OutlinedTextField(
                value = state.form.password,
                onValueChange = { value -> onUserFieldChange { it.copy(password = value) } },
                label = { Text("Hasło (opcjonalne przy edycji)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            )
            OutlinedTextField(
                value = state.form.role,
                onValueChange = { value -> onUserFieldChange { it.copy(role = value.uppercase()) } },
                label = { Text("Rola: ADMIN / TRAINER / CLIENT") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            )
            OutlinedTextField(
                value = state.form.firstName,
                onValueChange = { value -> onUserFieldChange { it.copy(firstName = value) } },
                label = { Text("Imię") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            )
            OutlinedTextField(
                value = state.form.lastName,
                onValueChange = { value -> onUserFieldChange { it.copy(lastName = value) } },
                label = { Text("Nazwisko") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            )
            OutlinedTextField(
                value = state.form.phoneNumber,
                onValueChange = { value -> onUserFieldChange { it.copy(phoneNumber = value) } },
                label = { Text("Telefon") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onSave,
                    colors = ButtonDefaults.buttonColors(containerColor = Green80, contentColor = Color.White),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Zapisz")
                }
                if (state.form.id.isNotBlank()) {
                    OutlinedButton(
                        onClick = onClear,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE53935)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Anuluj edycję")
                    }
                } else {
                    OutlinedButton(
                        onClick = onClear,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Wyczyść")
                    }
                }
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F2FF)),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Podopieczni trenera", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF7E57C2))
            OutlinedTextField(
                value = state.trainerIdForClients,
                onValueChange = onTrainerIdChange,
                label = { Text("ID trenera") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            )
            Button(onClick = onLoadTrainerClients, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7E57C2), contentColor = Color.White)) { Text("Pobierz klientów") }

            if (state.isTrainerClientsLoading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = Green80
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Ładowanie klientów...", fontSize = 13.sp, color = Color(0xFF666666))
                }
            }

            state.trainerClients.forEach { client ->
                Text("#${client.id} • ${client.firstName} ${client.lastName} • ${client.email}", fontWeight = FontWeight.Medium)
            }

            HorizontalDivider(color = Color(0xFFE0E0E0))

            OutlinedTextField(
                value = state.clientIdForAssignment,
                onValueChange = onClientIdChange,
                label = { Text("ID klienta") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAssignClient, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7E57C2), contentColor = Color.White)) { Text("Przypisz") }
                OutlinedButton(onClick = onUnassignClient) { Text("Odepnij") }
            }
        }
    }
}

@Composable
private fun AdminMembershipTypesSection(
    state: AdminDashboardUiState,
    onMembershipTypeFieldChange: ((AdminMembershipTypeFormState) -> AdminMembershipTypeFormState) -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit,
    onEditMembershipType: (biali.fitmanager.network.MembershipTypeResponse) -> Unit,
    onDeleteMembershipType: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8EE)),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Typy karnetów", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFFFF9800))

            state.membershipTypes.forEach { membershipType ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(12.dp)) {
                        Text("${membershipType.name} • ${membershipType.price}zł • ${membershipType.durationDays} dni", fontWeight = FontWeight.SemiBold)
                        if (membershipType.description != null) Text(membershipType.description, fontSize = 12.sp, color = Color.Gray)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { onEditMembershipType(membershipType) }) {
                                Icon(Icons.Filled.Edit, contentDescription = null)
                                Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                                Text("Edytuj")
                            }
                            OutlinedButton(onClick = { onDeleteMembershipType(membershipType.id) }) {
                                Icon(Icons.Filled.Delete, contentDescription = null)
                                Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                                Text("Usuń")
                            }
                        }
                    }
                }
            }

            HorizontalDivider()
            Text(if (state.membershipTypeForm.id.isBlank()) "Dodaj / edytuj typ karnetu" else "Edycja typu #${state.membershipTypeForm.id}", fontWeight = FontWeight.Bold, color = Color(0xFFFF9800))

            OutlinedTextField(
                value = state.membershipTypeForm.name,
                onValueChange = { value -> onMembershipTypeFieldChange { it.copy(name = value) } },
                label = { Text("Nazwa") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            )
            OutlinedTextField(
                value = state.membershipTypeForm.price,
                onValueChange = { value -> onMembershipTypeFieldChange { it.copy(price = value) } },
                label = { Text("Cena") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            )
            OutlinedTextField(
                value = state.membershipTypeForm.durationDays,
                onValueChange = { value -> onMembershipTypeFieldChange { it.copy(durationDays = value) } },
                label = { Text("Liczba dni") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            )
            OutlinedTextField(
                value = state.membershipTypeForm.description,
                onValueChange = { value -> onMembershipTypeFieldChange { it.copy(description = value) } },
                label = { Text("Opis (opcjonalnie)") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3,
                shape = RoundedCornerShape(8.dp)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onSave, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800), contentColor = Color.White)) { Text("Zapisz") }
                if (state.membershipTypeForm.id.isNotBlank()) {
                    OutlinedButton(
                        onClick = onClear,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE53935)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Anuluj edycję")
                    }
                } else {
                    OutlinedButton(
                        onClick = onClear,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Wyczyść")
                    }
                }
            }
        }
    }
}


