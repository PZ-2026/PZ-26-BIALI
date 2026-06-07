package biali.fitmanager

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import biali.fitmanager.network.ApiResult
import biali.fitmanager.network.FitManagerRepository
import biali.fitmanager.network.MembershipTypeResponse
import biali.fitmanager.network.PurchaseMembershipRequest
import biali.fitmanager.network.SessionManager
import biali.fitmanager.ui.theme.Green80
import biali.fitmanager.ui.theme.GymManagerTheme
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons

class MembershipsActivity : ComponentActivity() {
    private val repository = FitManagerRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            GymManagerTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = { MembershipsTopBar(onBack = { finish() }) },
                    bottomBar = {
                        FitBottomNav(
                            currentRoute = "memberships",
                            onNavigateToHome = { finish() },
                            onNavigateToTrainers = {
                                val intent = Intent(this@MembershipsActivity, TrainersActivity::class.java)
                                startActivity(intent)
                                finish()
                            },
                            onNavigateToMemberships = { },
                            onNavigateToProgress = {
                                val intent = Intent(this@MembershipsActivity, ProgressActivity::class.java)
                                startActivity(intent)
                                finish()
                            },
                            onNavigateToAccount = {
                                val intent = Intent(this@MembershipsActivity, AccountActivity::class.java)
                                startActivity(intent)
                            }
                        )
                    }
                ) { innerPadding ->
                    MembershipsContent(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }

    @Composable
    fun MembershipsContent(modifier: Modifier = Modifier) {
        var membershipTypes by remember { mutableStateOf<List<MembershipTypeResponse>>(emptyList()) }
        var loading by remember { mutableStateOf(true) }
        var errorMessage by remember { mutableStateOf<String?>(null) }

        // state to show selected type in dialog
        var selectedType by remember { mutableStateOf<MembershipTypeResponse?>(null) }
        val snackbarHostState = remember { SnackbarHostState() }
        val coroutineScope = rememberCoroutineScope()

        LaunchedEffect(Unit) {
            loading = true
            when (val result = repository.getMembershipTypes()) {
                is ApiResult.Success -> {
                    membershipTypes = result.data
                    errorMessage = null
                }
                is ApiResult.Unauthorized -> {
                    SessionManager.clearSession()
                    val intent = Intent(this@MembershipsActivity, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                    startActivity(intent)
                    finish()
                }
                is ApiResult.Error -> {
                    errorMessage = result.message ?: "Błąd podczas pobierania karnetów"
                }
            }
            loading = false
        }

        Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FBF9)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Wybierz karnet",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Green80
                    )
                    Text(
                        text = "Nowoczesne pakiety z czytelnym opisem i szybkim zakupem.",
                        fontSize = 13.sp,
                        color = Color(0xFF546E7A)
                    )
                }
            }

            // snackbar host
            SnackbarHost(hostState = snackbarHostState)

            if (loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    CircularProgressIndicator()
                }
            } else if (errorMessage != null) {
                Text(text = errorMessage ?: "Nieznany błąd", color = Color.Red)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(membershipTypes) { type ->
                        MembershipCard(type) { selected ->
                            // instead of immediate purchase, show confirmation dialog
                            selectedType = selected
                        }
                    }
                }
            }

            // pokaz dialog z podsumowaniem zakupu
            selectedType?.let { type ->
                PurchaseConfirmationDialog(
                    type = type,
                    onDismiss = { selectedType = null },
                    onConfirm = {
                        val balance = SessionManager.getBalance()
                        if (balance < type.price) {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Za mało środków")
                            }
                        } else {
                            // wykonaj zakup i po sukcesie zmniejsz saldo
                            lifecycleScope.launch {
                                // resolve userId from token or from server
                                val token = SessionManager.getToken()
                                var userId: Int? = token?.let { SessionManager.resolveUserIdFromToken(it) }
                                if (userId == null) {
                                    when (val meRes = repository.getMe()) {
                                        is ApiResult.Success -> userId = meRes.data.id
                                        is ApiResult.Unauthorized -> {
                                            SessionManager.clearSession()
                                            val intent = Intent(this@MembershipsActivity, MainActivity::class.java).apply {
                                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                            }
                                            startActivity(intent)
                                            finish()
                                            selectedType = null
                                            return@launch
                                        }
                                        is ApiResult.Error -> {
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar(meRes.message ?: "Nie można pobrać danych użytkownika")
                                            }
                                            selectedType = null
                                            return@launch
                                        }
                                    }
                                }

                                if (userId == null) {
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Nieznane ID użytkownika")
                                    }
                                    selectedType = null
                                    return@launch
                                }

                                val request = PurchaseMembershipRequest(userId = userId!!, membershipTypeId = type.id)
                                when (val result = repository.purchaseMembership(request)) {
                                    is ApiResult.Success -> {
                                        // po zakupie spróbuj pobrać profil z serwera i zapisać saldo
                                        when (val meRes = repository.getMe()) {
                                            is ApiResult.Success -> {
                                                meRes.data.balance?.let { SessionManager.saveBalance(it) }
                                                    ?: SessionManager.changeBalanceBy(-type.price)
                                            }
                                            else -> {
                                                // fallback: pomniejsz lokalnie
                                                SessionManager.changeBalanceBy(-type.price)
                                            }
                                        }
                                        // Po zakupie wróć do HomeActivity
                                        val intent = Intent(this@MembershipsActivity, HomeActivity::class.java).apply {
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                        }
                                        startActivity(intent)
                                        finish()
                                    }
                                    is ApiResult.Unauthorized -> {
                                        SessionManager.clearSession()
                                        val intent = Intent(this@MembershipsActivity, MainActivity::class.java).apply {
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                        }
                                        startActivity(intent)
                                        finish()
                                    }
                                    is ApiResult.Error -> {
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar(getPurchaseErrorMessage(result))
                                        }
                                    }
                                }
                            }
                        }
                        selectedType = null
                    }
                )
            }
        }
    }

    private fun purchaseMembership(type: MembershipTypeResponse) {
        lifecycleScope.launch {
            // resolve userId
            val token = SessionManager.getToken()
            var userId: Int? = token?.let { SessionManager.resolveUserIdFromToken(it) }
            if (userId == null) {
                when (val meRes = repository.getMe()) {
                    is ApiResult.Success -> userId = meRes.data.id
                    else -> {
                        // cannot resolve user id
                        return@launch
                    }
                }
            }

            val request = PurchaseMembershipRequest(userId = userId!!, membershipTypeId = type.id)
            when (val result = repository.purchaseMembership(request)) {
                is ApiResult.Success -> {
                    // Po zakupie wróć do HomeActivity
                    val intent = Intent(this@MembershipsActivity, HomeActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                    startActivity(intent)
                    finish()
                }
                is ApiResult.Unauthorized -> {
                    SessionManager.clearSession()
                    val intent = Intent(this@MembershipsActivity, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                    startActivity(intent)
                    finish()
                }
                is ApiResult.Error -> {
                    // Reserved for potential alternative flow.
                }
            }
        }
    }

    private fun getPurchaseErrorMessage(error: ApiResult.Error): String {
        return when (error.code) {
            409 -> "Masz już aktywny karnet. Nowy kupisz po wygaśnięciu obecnego."
            else -> error.message.ifBlank { "Błąd podczas zakupu" }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MembershipsTopBar(onBack: () -> Unit) {
    TopAppBar(
        title = { Text("Karnety") },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wstecz")
            }
        }
    )
}

@Composable
fun MembershipCard(type: MembershipTypeResponse, onPurchase: (MembershipTypeResponse) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(text = type.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Green80)
                    Text(text = "${type.durationDays} dni", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF37474F))
                }
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF8F1)),
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Text(
                        text = "${String.format(Locale.getDefault(), "%.2f", type.price)} zł",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        fontWeight = FontWeight.Bold,
                        color = Green80
                    )
                }
            }
            type.description?.let {
                Text(text = it, fontSize = 14.sp, color = Color(0xFF607D8B))
            }
            Button(
                onClick = { onPurchase(type) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Green80, contentColor = Color.White)
            ) {
                Text("Kup")
            }
        }
    }
}

@Composable
fun PurchaseConfirmationDialog(
    type: MembershipTypeResponse,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    // compute dates
    val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    val calendar = Calendar.getInstance()
    val startDate = calendar.time
    calendar.add(Calendar.DAY_OF_YEAR, type.durationDays)
    val endDate = calendar.time

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(text = "Podsumowanie zakupu", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Green80)

                Text(text = "Typ: ${type.name}")
                Text(text = "Ważny od: ${sdf.format(startDate)} do: ${sdf.format(endDate)}")
                Text(text = "Cena: ${String.format(Locale.getDefault(), "%.2f", type.price)} zł")

                val balance = SessionManager.getBalance()
                val remaining = balance - type.price
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FBF9))) {
                    Text(
                        text = "Saldo po zakupie: ${String.format(Locale.getDefault(), "%.2f", remaining)} zł",
                        modifier = Modifier.padding(12.dp),
                        fontWeight = FontWeight.SemiBold,
                        color = Green80
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text("Anuluj")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(containerColor = Green80)) {
                        Text("Kup", color = Color.White)
                    }
                }
            }
        }
    }
}

