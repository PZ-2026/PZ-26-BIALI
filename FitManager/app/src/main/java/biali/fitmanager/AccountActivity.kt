package biali.fitmanager

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import biali.fitmanager.network.*
import biali.fitmanager.ui.theme.Green80
import biali.fitmanager.ui.theme.GymManagerTheme
import kotlinx.coroutines.launch

class AccountActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SessionManager.initialize(applicationContext)
        enableEdgeToEdge()

        setContent {
            GymManagerTheme {
                AccountScreen(
                    onBack = { finish() },
                    onLogout = ::logout
                )
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { FitManagerRepository() }
    val scope = rememberCoroutineScope()

    // state fields
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var userId by remember { mutableIntStateOf(0) }
    var userRole by remember { mutableStateOf("") }

    // load current user data on first composition
    LaunchedEffect(Unit) {
        when (val result = repository.getMe()) {
            is ApiResult.Success -> {
                val me = result.data
                userId = me.id
                firstName = me.firstName ?: ""
                lastName = me.lastName ?: ""
                email = me.email
                phoneNumber = me.phoneNumber ?: ""
                userRole = me.role ?: SessionManager.getRole() ?: ""
            }
            is ApiResult.Unauthorized -> {
                Toast.makeText(context, "Sesja wygasła. Zaloguj się ponownie.", Toast.LENGTH_SHORT).show()
            }
            is ApiResult.Error -> {
                Toast.makeText(context, "Błąd ładowania danych: ${result.message}", Toast.LENGTH_SHORT).show()
            }
        }
        isLoading = false
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Konto") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Wstecz")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Green80,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { innerPadding ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Section: Basic info (non-editable)
                Text(
                    text = "Dane konta",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Green80
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = {},
                    label = { Text("Email") },
                    enabled = false,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = Color.Gray,
                        disabledBorderColor = Color.LightGray
                    )
                )

                HorizontalDivider(thickness = 1.dp, color = Color.LightGray)

                // Section: Editable profile fields
                Text(
                    text = "Profil",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Green80
                )

                OutlinedTextField(
                    value = firstName,
                    onValueChange = { firstName = it },
                    label = { Text("Imię") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = lastName,
                    onValueChange = { lastName = it },
                    label = { Text("Nazwisko") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = { phoneNumber = it },
                    label = { Text("Numer telefonu") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                )

                HorizontalDivider(thickness = 1.dp, color = Color.LightGray)

                // Section: Password change
                Text(
                    text = "Zmiana hasła",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Green80
                )

                Text(
                    text = "Pozostaw puste, jeśli nie chcesz zmieniać hasła.",
                    fontSize = 13.sp,
                    color = Color.Gray
                )

                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = { Text("Nowe hasło") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )

                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text("Potwierdź nowe hasło") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Save button
                Button(
                    onClick = {
                        if (newPassword != confirmPassword) {
                            Toast.makeText(context, "Hasła nie są zgodne.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        isSaving = true
                        scope.launch {
                            val request = UserUpsertRequest(
                                email = email,
                                firstName = firstName,
                                lastName = lastName,
                                phoneNumber = phoneNumber.ifBlank { null },
                                password = newPassword.ifBlank { null },
                                role = userRole
                            )

                            when (val result = repository.updateOwnProfile(userId, request)) {
                                is ApiResult.Success -> {
                                    Toast.makeText(context, "Dane zaktualizowane pomyślnie.", Toast.LENGTH_SHORT).show()
                                    // if password was changed, force re-login
                                    if (newPassword.isNotBlank()) {
                                        onLogout()
                                        return@launch
                                    }
                                    newPassword = ""
                                    confirmPassword = ""
                                }
                                is ApiResult.Unauthorized -> {
                                    Toast.makeText(context, "Sesja wygasła. Zaloguj się ponownie.", Toast.LENGTH_SHORT).show()
                                }
                                is ApiResult.Error -> {
                                    Toast.makeText(context, "Błąd: ${result.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                            isSaving = false
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    enabled = !isSaving,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Green80
                    )
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Zapisz zmiany", fontSize = 16.sp)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Logout button
                OutlinedButton(
                    onClick = onLogout,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.Red
                    )
                ) {
                    Text("Wyloguj się", fontSize = 16.sp)
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
