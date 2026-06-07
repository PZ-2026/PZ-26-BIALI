package biali.fitmanager

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import biali.fitmanager.network.SessionManager
import biali.fitmanager.network.FitManagerRepository
import biali.fitmanager.network.TopUpRequest
import biali.fitmanager.network.ApiResult
import biali.fitmanager.validation.InputValidator
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.launch
import biali.fitmanager.ui.theme.GymManagerTheme
import biali.fitmanager.ui.theme.Green80
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.shape.RoundedCornerShape

@OptIn(ExperimentalMaterial3Api::class)
class WalletActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        SessionManager.initialize(applicationContext)

        setContent {
            GymManagerTheme {
                Scaffold(topBar = {
                    TopAppBar(title = { Text("Stan konta") }, navigationIcon = {
                        IconButton(onClick = { finish() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wstecz")
                        }
                    })
                }, bottomBar = {
                    FitBottomNav(
                        currentRoute = "account",
                        onNavigateToHome = {
                            startActivity(Intent(this@WalletActivity, HomeActivity::class.java))
                            finish()
                        },
                        onNavigateToTrainers = {
                            startActivity(Intent(this@WalletActivity, TrainersActivity::class.java))
                            finish()
                        },
                        onNavigateToMemberships = {
                            startActivity(Intent(this@WalletActivity, MembershipsActivity::class.java))
                            finish()
                        },
                        onNavigateToProgress = {
                            startActivity(Intent(this@WalletActivity, ProgressActivity::class.java))
                            finish()
                        },
                        onNavigateToAccount = { }
                    )
                }) { innerPadding ->
                    WalletContent(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletContent(modifier: Modifier = Modifier) {
    var balance by remember { mutableStateOf(SessionManager.getBalance()) }
    var amountText by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }

    val repository = remember { FitManagerRepository() }
    val coroutineScope = rememberCoroutineScope()

    // fetch user balance from server on enter
    LaunchedEffect(Unit) {
        loading = true
        when (val meRes = repository.getMe()) {
            is ApiResult.Success -> {
                val serverBalance = meRes.data.balance
                if (serverBalance != null) {
                    balance = serverBalance
                    SessionManager.saveBalance(serverBalance)
                }
            }
            is ApiResult.Error -> {
                // leave local balance
            }
            is ApiResult.Unauthorized -> {
                // keep local, optional: navigate to login
            }
        }
        loading = false
    }

    if (loading) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FBF9)),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(text = "Stan konta", fontSize = 14.sp, color = Color(0xFF546E7A))
                    Text(
                        text = "${"%.2f".format(balance)} zł",
                        fontSize = 34.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Green80
                    )
                    Text(text = "Doładuj środki szybko i wygodnie", fontSize = 13.sp, color = Color(0xFF607D8B))
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(text = "Szybkie kwoty", fontWeight = FontWeight.Bold, color = Green80)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val amounts = listOf(10, 20, 50, 100, 200)

                        amounts.forEach { a ->
                            Button(
                                onClick = {
                                    amountText = String.format(
                                        java.util.Locale.getDefault(),
                                        "%.2f",
                                        a.toDouble()
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFEAF8F1),
                                    contentColor = Green80
                                )
                            ) {
                                Text(text = "${a} zł")
                            }
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFDFDFD)),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = { Text("Kwota do dodania") },
                        keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(onClick = {
                        val parsed = amountText.replace(',', '.').toDoubleOrNull()
                        val validationError = InputValidator.validateTopUpAmount(parsed)
                        if (validationError != null) {
                            message = validationError
                            return@Button
                        }

                        val token = SessionManager.getToken()
                        val maybeId = token?.let { SessionManager.resolveUserIdFromToken(it) }

                        coroutineScope.launch {
                            var userId: Int? = maybeId
                            if (userId == null) {
                                when (val meRes = repository.getMe()) {
                                    is ApiResult.Success -> userId = meRes.data.id
                                    is ApiResult.Error -> {
                                        message = meRes.message
                                    }
                                    is ApiResult.Unauthorized -> {
                                        message = "Brak autoryzacji"
                                    }
                                }
                            }

                            if (userId == null) return@launch

                            when (val res = repository.topUpUser(userId, TopUpRequest(amount = parsed!!))) {
                                is ApiResult.Success -> {
                                    when (val updated = repository.getMe()) {
                                        is ApiResult.Success -> {
                                            val serverBalance = updated.data.balance
                                            if (serverBalance != null) {
                                                SessionManager.saveBalance(serverBalance)
                                                balance = serverBalance
                                            } else {
                                                SessionManager.changeBalanceBy(parsed)
                                                balance = SessionManager.getBalance()
                                            }
                                        }
                                        else -> {
                                            SessionManager.changeBalanceBy(parsed)
                                            balance = SessionManager.getBalance()
                                        }
                                    }
                                    amountText = ""
                                    message = "Saldo zasilone o ${String.format(java.util.Locale.getDefault(), "%.2f", parsed)} zł"
                                }
                                is ApiResult.Error -> {
                                    message = res.message
                                }
                                is ApiResult.Unauthorized -> {
                                    message = "Brak autoryzacji"
                                }
                            }
                        }
                    }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Green80, contentColor = Color.White)) {
                        Text("Zasil konto")
                    }
                }
            }

            message?.let {
                Text(it, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}




