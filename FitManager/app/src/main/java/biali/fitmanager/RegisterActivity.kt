package biali.fitmanager

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import biali.fitmanager.network.FitManagerRepository
import biali.fitmanager.network.RegisterRequest
import biali.fitmanager.network.SessionManager
import biali.fitmanager.validation.InputValidator
import kotlinx.coroutines.launch

class RegisterActivity : AppCompatActivity() {

    private val repository = FitManagerRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SessionManager.initialize(applicationContext)
        setContentView(R.layout.activity_register)

        val etFirstName = findViewById<EditText>(R.id.etFirstName)
        val etLastName = findViewById<EditText>(R.id.etLastName)
        val etPhone = findViewById<EditText>(R.id.etPhone)
        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnRegister = findViewById<Button>(R.id.btnRegister)

        btnRegister.setOnClickListener {
            val firstName = etFirstName.text.toString().trim()
            val lastName = etLastName.text.toString().trim()
            val phone = etPhone.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString()

            val validationError = InputValidator.validateRegister(
                firstName = firstName,
                lastName = lastName,
                email = email,
                password = password,
                phone = phone.ifBlank { null }
            )
            if (validationError != null) {
                Toast.makeText(this, validationError, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                try {
                    val req = RegisterRequest(email, password, firstName, lastName, if (phone.isBlank()) null else phone)
                    when (val result = repository.register(req)) {
                        is biali.fitmanager.network.ApiResult.Success -> {
                            val token = result.data.token
                            if (token.isNullOrBlank()) {
                                Toast.makeText(this@RegisterActivity, "Zarejestrowano, jednak brak tokena.", Toast.LENGTH_SHORT).show()
                                finish()
                                return@launch
                            }
                            val role = SessionManager.resolveRoleFromToken(token)
                            SessionManager.saveSession(token, role)
                            Toast.makeText(this@RegisterActivity, "Zarejestrowano i zalogowano!", Toast.LENGTH_SHORT).show()
                            routeToPanel(role, email)
                        }
                        is biali.fitmanager.network.ApiResult.Error -> {
                            Toast.makeText(this@RegisterActivity, result.message, Toast.LENGTH_SHORT).show()
                        }
                        is biali.fitmanager.network.ApiResult.Unauthorized -> {
                            Toast.makeText(this@RegisterActivity, "Brak autoryzacji.", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (ex: Exception) {
                    Toast.makeText(this@RegisterActivity, "Błąd połączenia z serwerem.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun routeToPanel(role: String?, email: String?) {
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

        val intent = Intent(this, targetActivity).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        email?.let { intent.putExtra("USER_EMAIL", it) }
        intent.putExtra("USER_ROLE", role)
        startActivity(intent)
    }

    @Suppress("UNCHECKED_CAST")
    private fun resolveActivityClass(className: String): Class<out Activity>? {
        return runCatching {
            Class.forName(className) as Class<out Activity>
        }.getOrNull()
    }
}
