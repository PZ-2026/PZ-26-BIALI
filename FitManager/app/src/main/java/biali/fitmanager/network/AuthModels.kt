package biali.fitmanager.network

// Dane wysyłane do serwera
data class LoginRequest(
    val email: String,
    val password: String
)

// Dane odbierane z serwera
data class LoginResponse(
    val token: String
)