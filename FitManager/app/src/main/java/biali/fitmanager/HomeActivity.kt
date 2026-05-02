package biali.fitmanager

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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

class HomeActivity : ComponentActivity() {
    private val repository = FitManagerRepository()
    private var displayName by mutableStateOf("Użytkowniku")
    private var membership by mutableStateOf<MembershipResponse?>(null)
    private var membershipTypes by mutableStateOf<List<MembershipTypeResponse>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SessionManager.initialize(applicationContext)
        enableEdgeToEdge()

        val token = SessionManager.getToken()
        val loggedEmail = intent.getStringExtra("USER_EMAIL")
            ?: token?.let(SessionManager::resolveEmailFromToken)
        val loggedDisplayName = token?.let(SessionManager::resolveDisplayNameFromToken)

        displayName = loggedDisplayName ?: displayNameFromEmail(loggedEmail)
        fetchProfileDisplayNameIfNeeded()
        fetchMembership()
        fetchMembershipTypes()

        setContent {
            GymManagerTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = { Navbar(onLogout = ::logout, activeMembership = membership) },
                    bottomBar = { BottomNav(onNavigateToMemberships = ::navigateToMemberships) }
                ) { innerPadding ->
                    MainContent(
                        modifier = Modifier.padding(innerPadding),
                        displayName = displayName,
                        membership = membership,
                        membershipTypes = membershipTypes,
                        onBuyMembership = ::navigateToMemberships
                    )
                }
            }
        }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Navbar(onLogout: () -> Unit, activeMembership: MembershipResponse? = null) {
    TopAppBar(
        title = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(end = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("FitManager", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                activeMembership?.let {
                    Text("${it.membershipType.price} zł", fontSize = 16.sp)
                } ?: run {
                    Text("0.00 zł", fontSize = 16.sp)
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
    membershipTypes: List<MembershipTypeResponse>,
    onBuyMembership: () -> Unit
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
            membershipTypes = membershipTypes,
            onBuyMembership = onBuyMembership
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
    membershipTypes: List<MembershipTypeResponse>,
    onBuyMembership: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.LightGray.copy(alpha = 0.4f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (membership != null) {
                Text(
                    text = "Twój aktywny karnet",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Green80,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text("Typ: ${membership.membershipType.name}", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text("Cena: ${membership.membershipType.price} zł", fontSize = 16.sp)
                Text("Wygasa: ${membership.endDate}", fontSize = 16.sp)
            } else {
                Text(
                    text = "Brak aktywnego karnetu",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                if (membershipTypes.isNotEmpty()) {
                    Text("Dostępne opcje:", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    membershipTypes.forEach { type ->
                        Text("• ${type.name} - ${type.price} zł", fontSize = 14.sp)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Button(
                    onClick = onBuyMembership,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Green80, contentColor = Color.White)
                ) {
                    Text("Kup karnet")
                }
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

@Composable
fun BottomNav(onNavigateToMemberships: () -> Unit) {
    NavigationBar(
        containerColor = Color.White,
        tonalElevation = 8.dp
    ) {
        NavigationBarItem(
            selected = true,
            onClick = { /* Nawigacja do Home */ },
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
            onClick = { /* Nawigacja do Trenera */ },
            label = { Text("Trener") },
            icon = { Icon(Icons.Filled.Person, contentDescription = "Trener") }
        )
        NavigationBarItem(
            selected = false,
            onClick = { /* Nawigacja do Profilu */ },
            label = { Text("Postep") },
            icon = { Icon(Icons.Filled.Edit, contentDescription = "Postęp") }
        )
        NavigationBarItem(
            selected = false,
            onClick = onNavigateToMemberships,
            label = { Text("Karnety") },
            icon = { Icon(Icons.Filled.Person, contentDescription = "Karnety") }
        )
        NavigationBarItem(
            selected = false,
            onClick = { },
            label = { Text("Konto") },
            icon = { Icon(Icons.Filled.Person, contentDescription = "Konto") }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun MainContentPreview() {
    GymManagerTheme {
        MainContent(
            displayName = "Jan Nowak", 
            membership = null, 
            membershipTypes = emptyList(),
            onBuyMembership = {}
        )
    }
}