package biali.fitmanager

import android.os.Bundle
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
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import biali.fitmanager.ui.theme.GymManagerTheme

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
        Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text = "Aktualne saldo:", fontSize = 18.sp)
        Text(text = String.format(java.util.Locale.getDefault(), "%.2f zł", balance), fontSize = 28.sp)

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
            if (parsed == null || parsed <= 0.0) {
                message = "Wprowadź poprawną kwotę"
                return@Button
            }

            // perform server top-up
            val token = SessionManager.getToken()
            val maybeId = token?.let { SessionManager.resolveUserIdFromToken(it) }

            // use coroutineScope to call suspend functions
            coroutineScope.launch {
                var userId: Int? = maybeId
                if (userId == null) {
                    // fallback: try to get current user from API
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

                when (val res = repository.topUpUser(userId, TopUpRequest(amount = parsed))) {
                    is ApiResult.Success -> {
                        // on success, fetch fresh user info to get updated balance
                        when (val updated = repository.getMe()) {
                            is ApiResult.Success -> {
                                val serverBalance = updated.data.balance
                                if (serverBalance != null) {
                                    SessionManager.saveBalance(serverBalance)
                                    balance = serverBalance
                                } else {
                                    // fallback: increment locally
                                    SessionManager.changeBalanceBy(parsed)
                                    balance = SessionManager.getBalance()
                                }
                            }
                            else -> {
                                // fallback: increment locally
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
        }, modifier = Modifier.fillMaxWidth()) {
            Text("Zasil konto")
        }

            message?.let {
                Text(it, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}




