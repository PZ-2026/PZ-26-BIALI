package biali.fitmanager

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import biali.fitmanager.network.ApiResult
import biali.fitmanager.network.ChangePasswordRequest
import biali.fitmanager.network.FitManagerRepository
import biali.fitmanager.network.MeResponse
import biali.fitmanager.network.ProgressSummaryResponse
import biali.fitmanager.network.SessionManager
import biali.fitmanager.ui.theme.Green80
import biali.fitmanager.ui.theme.LightGreen80
import biali.fitmanager.ui.theme.GymManagerTheme
import kotlinx.coroutines.launch
import java.util.Locale

data class AccountStat(
    val label: String,
    val value: String,
    val note: String
)

@OptIn(ExperimentalMaterial3Api::class)
class AccountActivity : ComponentActivity() {
    private val repository = FitManagerRepository()

    private var profile by mutableStateOf<MeResponse?>(null)
    private var trainerDisplayName by mutableStateOf<String?>(null)
    private var progressSummary by mutableStateOf<ProgressSummaryResponse?>(null)
    private var roleStats by mutableStateOf<List<AccountStat>>(emptyList())
    private var loading by mutableStateOf(true)
    private var error by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SessionManager.initialize(applicationContext)
        enableEdgeToEdge()

        refreshAccountData()

        setContent {
            GymManagerTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        TopAppBar(
                            title = { Text("Konto", fontWeight = FontWeight.Bold) }
                        )
                    },
                    bottomBar = {
                        AccountBottomNav(
                            role = profile?.role ?: SessionManager.getRole(),
                            onCurrentScreen = { }
                        )
                    }
                ) { innerPadding ->
                    AccountScreen(
                        modifier = Modifier.padding(innerPadding),
                        loading = loading,
                        error = error,
                        profile = profile,
                        trainerDisplayName = trainerDisplayName,
                        progressSummary = progressSummary,
                        roleStats = roleStats,
                        role = profile?.role ?: SessionManager.getRole(),
                        onRefresh = ::refreshAccountData,
                        onLogout = ::logout
                    )
                }
            }
        }
    }

    private fun refreshAccountData() {
        loading = true
        error = null

        lifecycleScope.launch {
            when (val meResult = repository.getMe()) {
                is ApiResult.Success -> {
                    profile = meResult.data
                    trainerDisplayName = null

                    if (meResult.data.role.equals("CLIENT", ignoreCase = true) && meResult.data.trainerId != null) {
                        trainerDisplayName = when (val trainerResult = repository.getTrainerById(meResult.data.trainerId)) {
                            is ApiResult.Success -> listOf(trainerResult.data.firstName, trainerResult.data.lastName)
                                .filter { it.isNotBlank() }
                                .joinToString(" ")
                                .ifBlank { null }
                            else -> null
                        }
                    }

                    progressSummary = when (val progressResult = repository.getProgressSummary()) {
                        is ApiResult.Success -> progressResult.data
                        else -> null
                    }

                    val resolvedRole = meResult.data.role.ifBlank { SessionManager.getRole().orEmpty() }
                    roleStats = loadRoleStats(resolvedRole, meResult.data)
                }

                is ApiResult.Unauthorized -> {
                    logout()
                    return@launch
                }

                is ApiResult.Error -> {
                    error = meResult.message
                }
            }

            loading = false
        }
    }

    private suspend fun loadRoleStats(role: String, me: MeResponse): List<AccountStat> {
        return when {
            role.equals("ADMIN", ignoreCase = true) -> loadAdminStats()
            role.equals("TRAINER", ignoreCase = true) -> loadTrainerStats()
            else -> loadClientStats(me)
        }
    }

    private suspend fun loadClientStats(me: MeResponse): List<AccountStat> {
        val membershipResult = repository.getMyMembership()
        val sessionsResult = repository.getMyAssignedSessions()

        val membershipLabel = when (membershipResult) {
            is ApiResult.Success -> membershipResult.data.membershipType.name
            is ApiResult.Error -> "Brak aktywnego karnetu"
            is ApiResult.Unauthorized -> "Brak dostępu"
        }

        val sessionsCount = when (sessionsResult) {
            is ApiResult.Success -> sessionsResult.data.size
            else -> 0
        }

        return buildList {
            add(AccountStat("Karnet", membershipLabel, "Aktualny status abonamentu"))
            add(AccountStat("Trener", trainerDisplayName ?: trainerLabel(me), "Przypisany trener"))
            add(AccountStat("Sesje", sessionsCount.toString(), "Przydzielone treningi"))
            progressSummary?.let {
                add(AccountStat("Postęp", it.progressList.size.toString(), "Ćwiczenia zarejestrowane w historii"))
                add(AccountStat("Dni", it.daysSinceFirstTraining.toString(), "Od pierwszego treningu"))
            }
        }
    }

    private suspend fun loadTrainerStats(): List<AccountStat> {
        val clientsResult = repository.getMyTrainerClients()
        val membershipsResult = repository.getMemberships()
        val progressLogsResult = repository.getTrainerProgressLogs()
        val sessionsResult = repository.getTrainerSessions()

        val clientsCount = when (clientsResult) {
            is ApiResult.Success -> clientsResult.data.size
            else -> 0
        }
        val clientIds = when (clientsResult) {
            is ApiResult.Success -> clientsResult.data.map { it.id }.toSet()
            else -> emptySet()
        }
        val progressLogsCount = when (progressLogsResult) {
            is ApiResult.Success -> progressLogsResult.data.size
            else -> 0
        }
        val sessionsCount = when (sessionsResult) {
            is ApiResult.Success -> sessionsResult.data.size
            else -> 0
        }
        val revenueValue = when (membershipsResult) {
            is ApiResult.Success -> membershipsResult.data
                .filter { it.userId in clientIds }
                .sumOf { it.membershipType.price }
            else -> 0.0
        }

        return buildList {
            add(AccountStat("Podopieczni", clientsCount.toString(), "Aktywni klienci trenera"))
            add(AccountStat("Przychód", String.format(Locale.getDefault(), "%.2f zł", revenueValue), "Suma z karnetów podopiecznych"))
            add(AccountStat("Sesje", sessionsCount.toString(), "Zapisane plany treningowe"))
            add(AccountStat("Zapisy", progressLogsCount.toString(), "Logi postępu podopiecznych"))
            progressSummary?.let {
                add(AccountStat("Postęp", it.progressList.size.toString(), "Ćwiczenia w statystykach"))
            }
        }
    }

    private suspend fun loadAdminStats(): List<AccountStat> {
        val usersResult = repository.getUsers()
        val trainersResult = repository.getTrainers()
        val membershipsResult = repository.getMembershipTypes()
        val soldMembershipsResult = repository.getMemberships()

        val usersCount = when (usersResult) {
            is ApiResult.Success -> usersResult.data.size
            else -> 0
        }
        val trainersCount = when (trainersResult) {
            is ApiResult.Success -> trainersResult.data.size
            else -> 0
        }
        val membershipsCount = when (membershipsResult) {
            is ApiResult.Success -> membershipsResult.data.size
            else -> 0
        }
        val revenueValue = when (soldMembershipsResult) {
            is ApiResult.Success -> soldMembershipsResult.data.sumOf { it.membershipType.price }
            else -> 0.0
        }

        return buildList {
            add(AccountStat("Użytkownicy", usersCount.toString(), "Wszystkie konta w systemie"))
            add(AccountStat("Trenerzy", trainersCount.toString(), "Konta z rolą TRAINER"))
            add(AccountStat("Przychód z karnetów", String.format(Locale.getDefault(), "%.2f zł", revenueValue), "Suma wszystkich płatności za karnety"))
            add(AccountStat("Karnety", membershipsCount.toString(), "Dostępne typy karnetów"))
        }
    }

    private fun trainerLabel(me: MeResponse): String {
        return if (me.trainerId != null) "ID ${me.trainerId}" else "Brak trenera"
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

@Composable
private fun AccountScreen(
    modifier: Modifier = Modifier,
    loading: Boolean,
    error: String?,
    profile: MeResponse?,
    trainerDisplayName: String?,
    progressSummary: ProgressSummaryResponse?,
    roleStats: List<AccountStat>,
    role: String?,
    onRefresh: () -> Unit,
    onLogout: () -> Unit
) {
    val scrollState = rememberScrollState()
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var feedback by remember { mutableStateOf<String?>(null) }
    var dialogMessage by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val repository = remember { FitManagerRepository() }

    LaunchedEffect(error) {
        if (!error.isNullOrBlank()) {
            dialogMessage = error
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = "Mój profil", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(text = "Dane konta, hasło i statystyki w jednym miejscu", fontSize = 15.sp, color = Color.Gray)

        if (loading) {
            Box(modifier = Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Green80)
            }
        }

        profile?.let { me ->
            ProfileCard(me = me, trainerDisplayName = trainerDisplayName, hideBalance = me.role.equals("ADMIN", ignoreCase = true) || me.role.equals("TRAINER", ignoreCase = true))
        }

        if (roleStats.isNotEmpty()) {
            Text(text = "Statystyki", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            roleStats.forEach { stat ->
                AccountStatCard(stat = stat)
            }
        }

        if (!role.equals("ADMIN", ignoreCase = true)) {
            progressSummary?.let { summary ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(text = "Przegląd aktywności", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(text = "Zakres: ${summary.dateRange}", color = Color.DarkGray)
                        Text(text = "Dni od pierwszego treningu: ${summary.daysSinceFirstTraining}", color = Color.DarkGray)
                        Text(text = "Ćwiczenia w zestawieniu: ${summary.progressList.size}", color = Color.DarkGray)
                    }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = "Zmiana hasła", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = currentPassword,
                    onValueChange = { currentPassword = it },
                    label = { Text("Aktualne hasło") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = { Text("Nowe hasło") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text("Powtórz nowe hasło") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        val trimmedCurrent = currentPassword.trim()
                        val trimmedNew = newPassword.trim()
                        val trimmedConfirm = confirmPassword.trim()

                        if (trimmedCurrent.isBlank() || trimmedNew.length < 6) {
                            dialogMessage = "Hasło musi mieć co najmniej 6 znaków."
                            return@Button
                        }
                        if (trimmedNew != trimmedConfirm) {
                            dialogMessage = "Nowe hasła nie są identyczne."
                            return@Button
                        }

                        saving = true
                        feedback = null
                        coroutineScope.launch {
                            when (val result = repository.changeMyPassword(ChangePasswordRequest(trimmedCurrent, trimmedNew))) {
                                is ApiResult.Success -> {
                                    currentPassword = ""
                                    newPassword = ""
                                    confirmPassword = ""
                                    feedback = "Hasło zostało zmienione."
                                }
                                is ApiResult.Unauthorized -> {
                                    dialogMessage = "Sesja wygasła, zaloguj się ponownie."
                                    onLogout()
                                }
                                is ApiResult.Error -> {
                                    dialogMessage = when (result.code) {
                                        400 -> "Nie udało się zmienić hasła. Sprawdź aktualne hasło i spróbuj ponownie."
                                        401 -> "Sesja wygasła, zaloguj się ponownie."
                                        403 -> "Brak uprawnień do zmiany hasła."
                                        404 -> "Nie znaleziono konta lub zasobu."
                                        else -> "Nie udało się zmienić hasła. Spróbuj ponownie."
                                    }
                                }
                            }
                            saving = false
                        }
                    },
                    enabled = !saving,
                    colors = ButtonDefaults.buttonColors(containerColor = Green80, contentColor = Color.White),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    androidx.compose.material3.Icon(Icons.Filled.Lock, contentDescription = null)
                    Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                    Text(if (saving) "Zapisywanie..." else "Zmień hasło")
                }

                feedback?.let { Text(text = it, color = Color(0xFF2E7D32)) }
            }
        }

        Button(
            onClick = onLogout,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F), contentColor = Color.White),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Wyloguj się")
        }
    }

    dialogMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { dialogMessage = null },
            title = { Text("Komunikat") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { dialogMessage = null }) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
private fun ProfileCard(me: MeResponse, trainerDisplayName: String?, hideBalance: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(0.dp, 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = "Informacje o koncie", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            ProfileRow("Imię i nazwisko", listOf(me.firstName, me.lastName).filter { it.isNotBlank() }.joinToString(" "))
            ProfileRow("Email", me.email)
            ProfileRow("Rola", me.role)
            ProfileRow("Telefon", me.phoneNumber ?: "Brak")
            if (!hideBalance) {
                ProfileRow("Saldo", String.format(Locale.getDefault(), "%.2f zł", me.balance ?: SessionManager.getBalance()))
            }
            if (me.trainerId != null) {
                ProfileRow("Trener", trainerDisplayName ?: "ID ${me.trainerId}")
                ProfileRow("Koniec współpracy", me.trainerEndDate ?: "Brak daty")
            }
        }
    }
}

@Composable
private fun ProfileRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, color = Color.Gray)
        Text(text = value, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun AccountStatCard(stat: AccountStat) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF7FAF8))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = stat.label, color = Color.Gray, fontSize = 13.sp)
            Text(text = stat.value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Green80)
            Text(text = stat.note, color = Color.DarkGray)
        }
    }
}

@Composable
private fun AccountBottomNav(
    role: String?,
    onCurrentScreen: () -> Unit
) {
    val context = LocalContext.current

    if (role.equals("TRAINER", ignoreCase = true)) {
        NavigationBar(
            containerColor = Color.White,
            tonalElevation = 8.dp
        ) {
            NavigationBarItem(
                selected = false,
                onClick = { navigateByRole(context, role, "home") },
                label = { Text("Panel") },
                icon = { androidx.compose.material3.Icon(Icons.Filled.Home, contentDescription = "Panel") },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Green80,
                    selectedTextColor = Green80,
                    indicatorColor = LightGreen80
                )
            )
            NavigationBarItem(
                selected = false,
                onClick = { navigateByRole(context, role, "progress") },
                label = { Text("Postęp") },
                icon = { androidx.compose.material3.Icon(Icons.Filled.Edit, contentDescription = "Postęp") }
            )
            NavigationBarItem(
                selected = true,
                onClick = onCurrentScreen,
                label = { Text("Konto") },
                icon = { androidx.compose.material3.Icon(Icons.Filled.AccountCircle, contentDescription = "Konto") },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Green80,
                    selectedTextColor = Green80,
                    indicatorColor = LightGreen80
                )
            )
        }
    } else {
        FitBottomNav(
            currentRoute = "account",
            onNavigateToHome = {
                navigateByRole(context, role, "home")
            },
            onNavigateToTrainers = {
                navigateByRole(context, role, "trainers")
            },
            onNavigateToMemberships = {
                navigateByRole(context, role, "memberships")
            },
            onNavigateToProgress = {
                navigateByRole(context, role, "progress")
            },
            onNavigateToAccount = onCurrentScreen
        )
    }
}

private fun navigateByRole(context: android.content.Context, role: String?, route: String) {
    val target = when {
        role.equals("ADMIN", ignoreCase = true) -> when (route) {
            "home", "trainers", "memberships", "progress" -> AdminHomeActivity::class.java
            else -> AccountActivity::class.java
        }
        role.equals("TRAINER", ignoreCase = true) -> when (route) {
            "home", "trainers" -> TrainerUsersActivity::class.java
            "progress" -> TrainerProgressActivity::class.java
            "memberships" -> HomeActivity::class.java
            else -> AccountActivity::class.java
        }
        else -> when (route) {
            "home" -> HomeActivity::class.java
            "trainers" -> TrainersActivity::class.java
            "memberships" -> MembershipsActivity::class.java
            "progress" -> ProgressActivity::class.java
            else -> AccountActivity::class.java
        }
    }

    if (target == AccountActivity::class.java) return

    context.startActivity(Intent(context, target))
    if (context is ComponentActivity) {
        context.finish()
    }
}