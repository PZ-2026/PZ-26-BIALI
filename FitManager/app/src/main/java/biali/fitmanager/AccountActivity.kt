package biali.fitmanager

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import biali.fitmanager.network.ApiResult
import biali.fitmanager.network.ChangePasswordRequest
import biali.fitmanager.network.FitManagerRepository
import biali.fitmanager.network.MeResponse
import biali.fitmanager.network.ProgressSummaryResponse
import biali.fitmanager.network.SessionManager
import biali.fitmanager.network.UserUpsertRequest
import biali.fitmanager.ui.theme.Green80
import biali.fitmanager.validation.InputValidator
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
                            title = { Text("Konto", fontWeight = FontWeight.Bold) },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(Icons.Filled.ArrowBack, contentDescription = "Wstecz")
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = Green80,
                                titleContentColor = Color.White,
                                navigationIconContentColor = Color.White
                            )
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
        val revenueValue = clientsCount * TRAINER_RENTAL_PRICE

        return buildList {
            add(AccountStat("Podopieczni", clientsCount.toString(), "Aktywni klienci trenera"))
            add(AccountStat("Przychód", String.format(Locale.getDefault(), "%.2f zł", revenueValue), "Suma przychodu trenera"))
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
        val soldMembershipsCount = when (soldMembershipsResult) {
            is ApiResult.Success -> soldMembershipsResult.data.size
            else -> 0
        }
        val membershipPrice = when (membershipsResult) {
            is ApiResult.Success -> membershipsResult.data.firstOrNull()?.price ?: 0.0
            else -> 0.0
        }
        val revenueValue = soldMembershipsCount * membershipPrice

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
    val context = LocalContext.current
    val repository = remember { FitManagerRepository() }
    val coroutineScope = rememberCoroutineScope()

    // Profile editing state
    var editFirstName by remember { mutableStateOf("") }
    var editLastName by remember { mutableStateOf("") }
    var editPhoneNumber by remember { mutableStateOf("") }
    var editCurrentPassword by remember { mutableStateOf("") }
    var editPassword by remember { mutableStateOf("") }
    var editConfirmPassword by remember { mutableStateOf("") }
    var isSavingProfile by remember { mutableStateOf(false) }

    var dialogMessage by remember { mutableStateOf<String?>(null) }

    // Initialize editing fields when profile loads
    LaunchedEffect(profile) {
        profile?.let {
            editFirstName = it.firstName ?: ""
            editLastName = it.lastName ?: ""
            editPhoneNumber = it.phoneNumber ?: ""
        }
    }

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
        if (loading) {
            Box(modifier = Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Green80)
            }
        }

        profile?.let { me ->
            // ---- Profile Info Card (from main) ----
            ProfileCard(me = me, trainerDisplayName = trainerDisplayName, hideBalance = me.role.equals("ADMIN", ignoreCase = true) || me.role.equals("TRAINER", ignoreCase = true))
        }

        // ---- Role-specific Stats (from main) ----
        if (roleStats.isNotEmpty()) {
            Text(text = "Statystyki", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            roleStats.forEach { stat ->
                AccountStatCard(stat = stat)
            }
        }

        // ---- Progress Summary (from main, non-ADMIN) ----
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

        HorizontalDivider(thickness = 1.dp, color = Color.LightGray)

        // ---- Profile Editing Section (from our branch) ----
        Text(
            text = "Edytuj profil",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Green80
        )

        OutlinedTextField(
            value = editFirstName,
            onValueChange = { editFirstName = it },
            label = { Text("Imię") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = editLastName,
            onValueChange = { editLastName = it },
            label = { Text("Nazwisko") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = editPhoneNumber,
            onValueChange = { editPhoneNumber = it },
            label = { Text("Numer telefonu") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
        )

        Text(
            text = "Aby zmienić hasło, podaj aktualne hasło i nowe hasło (min. ${InputValidator.MIN_PASSWORD_LENGTH} znaków).",
            fontSize = 13.sp,
            color = Color.Gray
        )

        OutlinedTextField(
            value = editCurrentPassword,
            onValueChange = { editCurrentPassword = it },
            label = { Text("Aktualne hasło") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )

        OutlinedTextField(
            value = editPassword,
            onValueChange = { editPassword = it },
            label = { Text("Nowe hasło (opcjonalnie)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )

        OutlinedTextField(
            value = editConfirmPassword,
            onValueChange = { editConfirmPassword = it },
            label = { Text("Potwierdź nowe hasło") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )

        Button(
            onClick = {
                val profileError = InputValidator.validateProfileUpdate(
                    firstName = editFirstName.trim(),
                    lastName = editLastName.trim(),
                    phone = editPhoneNumber.trim().ifBlank { null }
                )
                if (profileError != null) {
                    dialogMessage = profileError
                    return@Button
                }

                val changingPassword = editPassword.isNotBlank() || editConfirmPassword.isNotBlank()
                if (changingPassword) {
                    val passwordError = InputValidator.validatePasswordChange(
                        currentPassword = editCurrentPassword,
                        newPassword = editPassword,
                        confirmPassword = editConfirmPassword
                    )
                    if (passwordError != null) {
                        dialogMessage = passwordError
                        return@Button
                    }
                }

                isSavingProfile = true
                coroutineScope.launch {
                    val request = UserUpsertRequest(
                        email = profile?.email ?: "",
                        firstName = editFirstName.trim(),
                        lastName = editLastName.trim(),
                        phoneNumber = editPhoneNumber.trim().ifBlank { null },
                        password = null,
                        role = profile?.role ?: SessionManager.getRole() ?: ""
                    )

                    when (val result = repository.updateOwnProfile(profile?.id ?: 0, request)) {
                        is ApiResult.Success -> {
                            if (changingPassword) {
                                when (val passwordResult = repository.changeMyPassword(
                                    ChangePasswordRequest(editCurrentPassword, editPassword)
                                )) {
                                    is ApiResult.Success -> {
                                        Toast.makeText(
                                            context,
                                            "Dane i hasło zaktualizowane. Zaloguj się ponownie.",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        onLogout()
                                        return@launch
                                    }
                                    is ApiResult.Unauthorized -> {
                                        dialogMessage = "Sesja wygasła. Zaloguj się ponownie."
                                    }
                                    is ApiResult.Error -> {
                                        dialogMessage = passwordResult.message
                                    }
                                }
                            } else {
                                Toast.makeText(context, "Dane zaktualizowane pomyślnie.", Toast.LENGTH_SHORT).show()
                            }
                            editCurrentPassword = ""
                            editPassword = ""
                            editConfirmPassword = ""
                        }
                        is ApiResult.Unauthorized -> {
                            dialogMessage = "Sesja wygasła. Zaloguj się ponownie."
                        }
                        is ApiResult.Error -> {
                            dialogMessage = "Błąd: ${result.message}"
                        }
                    }
                    isSavingProfile = false
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            enabled = !isSavingProfile,
            colors = ButtonDefaults.buttonColors(containerColor = Green80)
        ) {
            if (isSavingProfile) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            } else {
                Text("Zapisz zmiany w profilu", fontSize = 16.sp)
            }
        }

        // ---- Logout Button ----
        OutlinedButton(
            onClick = onLogout,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)
        ) {
            Text("Wyloguj się", fontSize = 16.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))
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
                icon = { Icon(Icons.Filled.Home, contentDescription = "Panel") },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Green80,
                    selectedTextColor = Green80,
                    indicatorColor = LightGreen80
                )
            )
            NavigationBarItem(
                selected = false,
                onClick = { navigateByRole(context, role, "trainers") },
                label = { Text("Podopieczni") },
                icon = { Icon(Icons.Filled.Person, contentDescription = "Podopieczni") },
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
                icon = { Icon(Icons.Filled.Edit, contentDescription = "Postęp") }
            )
            NavigationBarItem(
                selected = true,
                onClick = onCurrentScreen,
                label = { Text("Konto") },
                icon = { Icon(Icons.Filled.AccountCircle, contentDescription = "Konto") },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Green80,
                    selectedTextColor = Green80,
                    indicatorColor = LightGreen80
                )
            )
        }
    } else {
        AdminBottomNav(
            currentRoute = "account",
            onNavigateToPanel = { navigateByRole(context, role, "home") },
            onNavigateToTrainers = { navigateByRole(context, role, "trainers") },
            onNavigateToProgress = { navigateByRole(context, role, "home") },
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
