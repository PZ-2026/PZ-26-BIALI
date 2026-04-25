package biali.fitmanager

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import biali.fitmanager.network.LoginRequest
import biali.fitmanager.network.FitManagerRepository
import biali.fitmanager.network.SessionManager
import kotlinx.coroutines.launch


class MainActivity : AppCompatActivity() {

    private val repository = FitManagerRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SessionManager.initialize(applicationContext)

        if (routeToPanelIfSessionExists()) return
        setContentView(R.layout.activity_main)

        // Inicjalizacja widoków
        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val btnRegister = findViewById<Button>(R.id.btnRegister)
        val ivPasswordToggle = findViewById<ImageView>(R.id.ivPasswordToggle)

        var isPasswordVisible = false

        // Obsługa kliknięcia "Zaloguj się"
        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (email.isNotEmpty() && password.isNotEmpty()) {
                performLogin(email, password)
            } else {
                Toast.makeText(this, "Wpisz dane logowania!", Toast.LENGTH_SHORT).show()
            }
        }

        // Obsługa kliknięcia "Zarejestruj się"
        btnRegister.setOnClickListener {
            Toast.makeText(this, "Funkcja rejestracji będzie dostępna wkrótce!", Toast.LENGTH_SHORT).show()
        }

        // Obsługa przełącznika widoczności hasła
        ivPasswordToggle.setOnClickListener {
            if (isPasswordVisible) {
                etPassword.transformationMethod = PasswordTransformationMethod.getInstance()
                ivPasswordToggle.setImageResource(android.R.drawable.ic_menu_view)
            } else {
                etPassword.transformationMethod = HideReturnsTransformationMethod.getInstance()
                ivPasswordToggle.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            }
            isPasswordVisible = !isPasswordVisible
            etPassword.setSelection(etPassword.text.length)
        }
    }

    private fun performLogin(email: String, password: String) {
        lifecycleScope.launch {
            try {
                val request = LoginRequest(email, password)
                when (val result = repository.login(request)) {
                    is biali.fitmanager.network.ApiResult.Success -> {
                        val token = result.data.token
                        val role = SessionManager.resolveRoleFromToken(token)
                        if (role == null) {
                            Toast.makeText(this@MainActivity, "Nie udało się ustalić roli użytkownika.", Toast.LENGTH_SHORT).show()
                            return@launch
                        }

                        SessionManager.saveSession(token, role)
                        Toast.makeText(this@MainActivity, "Zalogowano!", Toast.LENGTH_SHORT).show()
                        routeToPanel(role, email)
                    }

                    is biali.fitmanager.network.ApiResult.Unauthorized -> {
                        Toast.makeText(this@MainActivity, "Błędne dane!", Toast.LENGTH_SHORT).show()
                    }

                    is biali.fitmanager.network.ApiResult.Error -> {
                        Toast.makeText(this@MainActivity, result.message, Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (_: Exception) {
                Toast.makeText(this@MainActivity, "Błąd połączenia z serwerem!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun routeToPanelIfSessionExists(): Boolean {
        val token = SessionManager.getToken()
        val role = SessionManager.getRole() ?: token?.let(SessionManager::resolveRoleFromToken)
        val email = token?.let(SessionManager::resolveEmailFromToken)

        if (!token.isNullOrBlank() && !role.isNullOrBlank()) {
            routeToPanel(role, email)
            return true
        }

        return false
    }

    private fun routeToPanel(role: String, email: String? = null) {
        val className: String? = if (role.equals("ADMIN", ignoreCase = true)) {
            "biali.fitmanager.AdminHomeActivity"
        } else if (role.equals("TRAINER", ignoreCase = true)) {
            "biali.fitmanager.TrainerUsersActivity"
        } else if (role.equals("CLIENT", ignoreCase = true)) {
            "biali.fitmanager.HomeActivity"
        } else {
            null
        }

        if (className == null) {
            Toast.makeText(this, "Nieobsługiwana rola: $role", Toast.LENGTH_SHORT).show()
            return
        }

        val targetActivity = resolveActivityClass(className)
        if (targetActivity == null) {
            Toast.makeText(this, "Nie udało się uruchomić panelu: $role", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(this, targetActivity)

        email?.let { intent.putExtra("USER_EMAIL", it) }
        intent.putExtra("USER_ROLE", role)
        startActivity(intent)
        finish()
    }

    @Suppress("UNCHECKED_CAST")
    private fun resolveActivityClass(className: String): Class<out Activity>? {
        return runCatching {
            Class.forName(className) as Class<out Activity>
        }.getOrNull()
    }
}