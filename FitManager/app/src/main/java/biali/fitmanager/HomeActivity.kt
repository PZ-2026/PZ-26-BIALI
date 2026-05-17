package biali.fitmanager

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import biali.fitmanager.network.ApiResult
import biali.fitmanager.network.FitManagerRepository
import biali.fitmanager.network.MembershipResponse
import biali.fitmanager.network.MembershipTypeResponse
import biali.fitmanager.network.SessionManager
import biali.fitmanager.ui.theme.Green80
import biali.fitmanager.ui.theme.LightGreen80
import biali.fitmanager.ui.theme.GymManagerTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class HomeActivity : ComponentActivity() {
    private val repository = FitManagerRepository()
    private var displayName by mutableStateOf("Użytkowniku")
    private var userRole by mutableStateOf<String?>(null)
    private var membership by mutableStateOf<MembershipResponse?>(null)
    private var isMembershipLoading by mutableStateOf(true)
    private var balance by mutableStateOf<Double?>(null)
    private var membershipTypes by mutableStateOf<List<MembershipTypeResponse>>(emptyList())
    private var trainerName by mutableStateOf<String?>(null)
    private var trainerStartDate by mutableStateOf<String?>(null)
    private var trainerEndDate by mutableStateOf<String?>(null)
    private var currentTrainerId by mutableStateOf<Int?>(null)
    private var isTrainerLoading by mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SessionManager.initialize(applicationContext)
        enableEdgeToEdge()

        val token = SessionManager.getToken()
        val loggedEmail = intent.getStringExtra("USER_EMAIL")
            ?: token?.let(SessionManager::resolveEmailFromToken)
        val loggedDisplayName = token?.let(SessionManager::resolveDisplayNameFromToken)

        userRole = SessionManager.getRole() ?: token?.let(SessionManager::resolveRoleFromToken)
        displayName = loggedDisplayName ?: displayNameFromEmail(loggedEmail)
        fetchProfileDisplayNameIfNeeded()
        fetchMembership()
        fetchBalance()
        fetchMembershipTypes()
        fetchTrainerStatus()

        setContent {
            GymManagerTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = { Navbar(onLogout = ::logout, balance = balance, onBalanceClick = ::navigateToWallet, userRole = userRole) },
                    bottomBar = { 
                        FitBottomNav(
                            currentRoute = "home",
                            onNavigateToHome = { /* Już tu jesteśmy */ },
                            onNavigateToTrainers = ::navigateToTrainers,
                            onNavigateToMemberships = ::navigateToMemberships,
                                    onNavigateToProgress = ::navigateToProgress
                        ) 
                    }
                ) { innerPadding ->
                    MainContent(
                        modifier = Modifier.padding(innerPadding),
                        displayName = displayName,
                        membership = membership,
                        isMembershipLoading = isMembershipLoading,
                        membershipTypes = membershipTypes,
                        onBuyMembership = ::navigateToMemberships,
                        trainerName = trainerName,
                        trainerStartDate = trainerStartDate,
                        trainerEndDate = trainerEndDate,
                        isTrainerLoading = isTrainerLoading,
                        onTrainerClick = {
                            currentTrainerId?.let { navigateToTrainerDetails(it) }
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // refresh critical user data when returning to Home
        fetchBalance()
        fetchMembership()
        fetchTrainerStatus()
    }

    private fun fetchBalance() {
        lifecycleScope.launch {
            when (val result = repository.getMe()) {
                is ApiResult.Success -> {
                    balance = result.data.balance ?: SessionManager.getBalance()
                }
                is ApiResult.Unauthorized -> logout()
                is ApiResult.Error -> {
                    // keep local stored balance if available
                    balance = SessionManager.getBalance()
                }
            }
        }
    }

    private fun navigateToWallet() {
        val intent = Intent(this, WalletActivity::class.java)
        startActivity(intent)
    }

    private fun fetchProfileDisplayNameIfNeeded() {
        if (displayName != "Użytkowniku") return

        lifecycleScope.launch {
            when (val result = repository.getMe()) {
                is ApiResult.Success -> {
                    val fromParts = listOf(result.data.firstName, result.data.lastName)
                        .filter { it.isNotBlank() }
                        .joinToString(" ")

                    displayName = when {
                        fromParts.isNotBlank() -> fromParts
                        !result.data.name.isNullOrBlank() -> result.data.name
                        else -> displayName
                    }
                }
                is ApiResult.Unauthorized -> logout()
                is ApiResult.Error -> Unit
            }
        }
    }

    private fun fetchMembership() {
        lifecycleScope.launch {
            isMembershipLoading = true
            when (val result = repository.getMyMembership()) {
                is ApiResult.Success -> {
                    membership = result.data
                }
                is ApiResult.Unauthorized -> logout()
                is ApiResult.Error -> {
                    if (result.code == 404) {
                        membership = null
                    }
                }
            }
            isMembershipLoading = false
        }
    }

    private fun fetchMembershipTypes() {
        lifecycleScope.launch {
            when (val result = repository.getMembershipTypes()) {
                is ApiResult.Success -> {
                    membershipTypes = result.data
                }
                else -> Unit
            }
        }
    }

    private fun fetchTrainerStatus() {
        lifecycleScope.launch {
            isTrainerLoading = true
            when (val meResult = repository.getMe()) {
                is ApiResult.Success -> {
                    val activeTrainerId = meResult.data.trainerId
                    val rawEndDate = meResult.data.trainerEndDate
                    currentTrainerId = activeTrainerId

                    if (activeTrainerId == null) {
                        currentTrainerId = null
                        trainerName = null
                        trainerStartDate = null
                        trainerEndDate = null
                    } else {
                        trainerEndDate = rawEndDate?.let(::formatIsoDateForDisplay)
                        trainerStartDate = rawEndDate?.let(::calculateTrainerStartDate)

                        when (val trainerResult = repository.getTrainerById(activeTrainerId)) {
                            is ApiResult.Success -> {
                                trainerName = "${trainerResult.data.firstName} ${trainerResult.data.lastName}".trim()
                            }
                            is ApiResult.Unauthorized -> {
                                logout()
                                return@launch
                            }
                            is ApiResult.Error -> {
                                trainerName = "Twój trener"
                            }
                        }
                    }
                }
                is ApiResult.Unauthorized -> {
                    logout()
                    return@launch
                }
                is ApiResult.Error -> {
                    currentTrainerId = null
                    trainerName = null
                    trainerStartDate = null
                    trainerEndDate = null
                }
            }
            isTrainerLoading = false
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

    private fun navigateToMemberships() {
        val intent = Intent(this, MembershipsActivity::class.java)
        startActivity(intent)
    }

    private fun navigateToTrainers() {
        val intent = Intent(this, TrainersActivity::class.java)
        startActivity(intent)
    }

    private fun navigateToTrainerDetails(trainerId: Int) {
        val intent = Intent(this, TrainersActivity::class.java).apply {
            putExtra("OPEN_TRAINER_ID", trainerId)
        }
        startActivity(intent)
    }

    private fun navigateToProgress() {
        val intent = Intent(this, ProgressActivity::class.java)
        startActivity(intent)
    }
}

private fun displayNameFromEmail(email: String?): String {
    if (email.isNullOrBlank()) return "Użytkowniku"
    val localPart = email.substringBefore('@')
    if (!localPart.contains('.') && !localPart.contains('_')) {
        return "Użytkowniku"
    }
    val tokens = localPart
        .replace('_', ' ')
        .replace('.', ' ')
        .split(' ')
        .filter { it.isNotBlank() }

    if (tokens.isEmpty()) return email

    return tokens.joinToString(" ") { token ->
        token.lowercase().replaceFirstChar { char -> char.uppercase() }
    }
}

private fun formatIsoDateForDisplay(rawDate: String): String {
    val input = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val output = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    val parsed = runCatching { input.parse(rawDate) }.getOrNull() ?: return rawDate
    return output.format(parsed)
}

private fun calculateTrainerStartDate(rawEndDate: String): String {
    val input = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val output = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    val parsedEndDate = runCatching { input.parse(rawEndDate) }.getOrNull() ?: return "-"
    val calendar = Calendar.getInstance().apply {
        time = parsedEndDate
        add(Calendar.DAY_OF_YEAR, -TRAINER_RENTAL_DURATION_DAYS)
    }
    return output.format(calendar.time)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Navbar(onLogout: () -> Unit, balance: Double? = null, onBalanceClick: () -> Unit = {}, userRole: String? = null) {
    val isAdmin = userRole.equals("ADMIN", ignoreCase = true)
    val isTrainer = userRole.equals("TRAINER", ignoreCase = true)
    
    TopAppBar(
        title = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(end = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("FitManager", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                // show balance only for CLIENT role
                if (!isAdmin && !isTrainer) {
                    TextButton(onClick = onBalanceClick) {
                        val display = balance ?: SessionManager.getBalance()
                        Text("${String.format(java.util.Locale.getDefault(), "%.2f", display)} zł", fontSize = 16.sp)
                    }
                }
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
fun MainContent(
    modifier: Modifier = Modifier, 
    displayName: String, 
    membership: MembershipResponse?, 
    isMembershipLoading: Boolean,
    membershipTypes: List<MembershipTypeResponse>,
    onBuyMembership: () -> Unit,
    trainerName: String?,
    trainerStartDate: String?,
    trainerEndDate: String?,
    isTrainerLoading: Boolean,
    onTrainerClick: () -> Unit
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
        Text(text = "Witaj $displayName!", fontSize = 16.sp)

        MembershipSection(
            membership = membership, 
            isMembershipLoading = isMembershipLoading,
            membershipTypes = membershipTypes,
            onBuyMembership = onBuyMembership
        )

        TrainerSection(
            trainerName = trainerName,
            trainerStartDate = trainerStartDate,
            trainerEndDate = trainerEndDate,
            isTrainerLoading = isTrainerLoading,
            onClick = onTrainerClick
        )

        Text(text = "Twoje ostatnie treningi", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)

        // Trening 1
        TrainingTable(date = "17.03.2026 r.")

        // Trening 2
        TrainingTable(date = "15.03.2026 r.")

        // Przycisk na samym dole
        Button(
            onClick = { println("Nawigacja do nowej strony...") },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Green80, contentColor = Color.White)
        ) {
            Text("Rozpocznij nowy trening", color = Color.White)
        }

        // Mały odstęp na samym dole, żeby przycisk nie dotykał krawędzi ekranu
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun MembershipSection(
    membership: MembershipResponse?,
    isMembershipLoading: Boolean,
    membershipTypes: List<MembershipTypeResponse>,
    onBuyMembership: () -> Unit
) {

    val hasMembership = membership != null

    val backgroundColor = when {
        isMembershipLoading -> Color.LightGray.copy(alpha = 0.4f)
        hasMembership -> Color(0xFF4CAF50) // zielony
        else -> Color(0xFFF44336) // czerwony
    }

    val textColor = if (isMembershipLoading) Color.Black else Color.White

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            if (isMembershipLoading) {
                Text(
                    text = "Sprawdzam karnet...",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = textColor,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                CircularProgressIndicator()
            }

            else if (membership != null) {

                Text(
                    text = "Twój aktywny karnet",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Text(
                    "Typ: ${membership.membershipType.name}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = textColor
                )

                Text(
                    "Cena: ${membership.membershipType.price} zł",
                    fontSize = 16.sp,
                    color = textColor
                )

                Text(
                    "Wygasa: ${membership.endDate}",
                    fontSize = 16.sp,
                    color = textColor
                )
            }

            else {

                Text(
                    text = "Brak aktywnego karnetu",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    modifier = Modifier.padding(bottom = 8.dp)

                )

                if (membershipTypes.isNotEmpty()) {
                    Text(
                        "Dostępne opcje:",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = textColor
                    )

                    membershipTypes.forEach { type ->
                        Text(
                            "• ${type.name} - ${type.price} zł",
                            fontSize = 14.sp,
                            color = textColor
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }

                Button(
                    onClick = onBuyMembership,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Black,
                        contentColor = Color.White
                    )
                ) {
                    Text("Kup karnet")
                }
            }
        }
    }
}

@Composable
fun TrainerSection(
    trainerName: String?,
    trainerStartDate: String?,
    trainerEndDate: String?,
    isTrainerLoading: Boolean,
    onClick: () -> Unit
) {
    val hasTrainer = !trainerName.isNullOrBlank()

    val backgroundColor = when {
        isTrainerLoading -> Color.LightGray.copy(alpha = 0.4f)
        hasTrainer -> Color(0xFF4CAF50)
        else -> Color(0xFFF44336)
    }

    val textColor = if (isTrainerLoading) Color.Black else Color.White

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (hasTrainer) Modifier.clickable(onClick = onClick) else Modifier),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (isTrainerLoading) {
                Text(
                    text = "Sprawdzam trenera...",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = textColor,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                CircularProgressIndicator()
            } else if (hasTrainer) {
                Text(
                    text = "Twój trener personalny",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Text(
                    text = "Trener: $trainerName",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = textColor
                )

                Text(
                    text = "Od: ${trainerStartDate ?: "-"}",
                    fontSize = 16.sp,
                    color = textColor
                )

                Text(
                    text = "Do: ${trainerEndDate ?: "-"}",
                    fontSize = 16.sp,
                    color = textColor
                )
            } else {
                Text(
                    text = "Brak trenera personalnego",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
            }
        }
    }
}

// Wyodrębniłem tabelę do osobnej funkcji, żeby nie powtarzać kodu (DRY)
@Composable
fun TrainingTable(date: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = date,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 2.dp)
        )

        Row(modifier = Modifier.fillMaxWidth()) {
            TableCell(text = "Nazwa ćwiczenia", weight = 2f, isHeader = true)
            TableCell(text = "Ciężar", weight = 1f, isHeader = true)
            TableCell(text = "Powtórzenia", weight = 1f, isHeader = true)
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), thickness = 1.dp, color = Color.LightGray)

        TrainRow("Podciągania", "10kg", "7 7 7 7 7")
        TrainRow("Martwy ciąg", "100kg", "7 8 6 9 5")
        TrainRow("Wiosłowanie", "60kg", "8 8 6 7 5")
        TrainRow("Dipy", "10kg", "6 6 8 8 6")
    }
}

@Composable
fun TrainRow(col1: String, col2: String, col3: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TableCell(text = col1, weight = 2f)
        TableCell(text = col2, weight = 1f)
        TableCell(text = col3, weight = 1f)
    }
}

@Composable
fun RowScope.TableCell(text: String, weight: Float, isHeader: Boolean = false) {
    Text(
        text = text,
        modifier = Modifier.weight(weight),
        fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
        fontSize = 14.sp
    )
}

@Preview(showBackground = true)
@Composable
fun MainContentPreview() {
    GymManagerTheme {
        MainContent(
            displayName = "Jan Nowak", 
            membership = null, 
            isMembershipLoading = false,
            membershipTypes = emptyList(),
            onBuyMembership = {},
            trainerName = null,
            trainerStartDate = null,
            trainerEndDate = null,
            isTrainerLoading = false,
            onTrainerClick = {}
        )
    }
}