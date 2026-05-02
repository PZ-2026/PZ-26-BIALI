package biali.fitmanager

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
                    topBar = { MembershipsTopBar(onBack = { finish() }) }
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

        Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
            Text(
                text = "Wybierz karnet",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    CircularProgressIndicator()
                }
            } else if (errorMessage != null) {
                Text(text = errorMessage ?: "Nieznany błąd", color = Color.Red)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(membershipTypes) { type ->
                        MembershipCard(type) { selectedType ->
                            purchaseMembership(selectedType)
                        }
                    }
                }
            }
        }
    }

    private fun purchaseMembership(type: MembershipTypeResponse) {
        lifecycleScope.launch {
            val request = PurchaseMembershipRequest(membershipTypeId = type.id)
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
                    // Pokaż błąd, np. Toast
                }
            }
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
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = type.name, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "Cena: ${type.price} zł", fontSize = 16.sp)
            Text(text = "Czas trwania: ${type.durationDays} dni", fontSize = 16.sp)
            type.description?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = it, fontSize = 14.sp, color = Color.Gray)
            }
            Spacer(modifier = Modifier.height(16.dp))
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
