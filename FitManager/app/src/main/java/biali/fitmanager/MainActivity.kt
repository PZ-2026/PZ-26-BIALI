package biali.fitmanager

import android.content.Intent
import android.os.Bundle
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import biali.fitmanager.network.LoginRequest
import biali.fitmanager.network.RetrofitClient
import kotlinx.coroutines.launch


class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                val response = RetrofitClient.api.login(request)

                if (response.isSuccessful && response.body() != null) {
                    val token = response.body()!!.token
                    Log.d("FITMANAGER_TEST", "SUKCES! Pobrany token: $token")

                    // Zapisujemy token (SharedPreferences)
                    saveToken(token)

                    Toast.makeText(this@MainActivity, "Zalogowano!", Toast.LENGTH_SHORT).show()
                    val intent = Intent(this@MainActivity, HomeActivity::class.java)
                    startActivity(intent)
                    finish()

                } else {
                    Log.e("FITMANAGER_TEST", "Błąd logowania, kod: ${response.code()}")
                    Toast.makeText(this@MainActivity, "Błędne dane!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("FITMANAGER_TEST", "Błąd połączenia: ${e.message}")
                Toast.makeText(this@MainActivity, "Błąd połączenia z serwerem!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveToken(token: String) {
        val sharedPref = getSharedPreferences("FitManagerPrefs", MODE_PRIVATE)
        with(sharedPref.edit()) {
            putString("JWT_TOKEN", token)
            apply()
        }
    }
}