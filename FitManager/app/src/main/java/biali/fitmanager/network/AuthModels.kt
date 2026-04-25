package biali.fitmanager.network

data class LoginRequest(
    val email: String,
    val password: String
)

data class LoginResponse(
    val token: String
)

data class ErrorResponse(
    val message: String
)

data class UserResponse(
    val id: Int,
    val email: String,
    val role: String,
    val firstName: String,
    val lastName: String,
    val phoneNumber: String? = null,
    val createdAt: String
)

data class MeResponse(
    val id: Int,
    val email: String,
    val role: String,
    val firstName: String,
    val lastName: String,
    val phoneNumber: String? = null,
    val name: String? = null
)

data class UserUpsertRequest(
    val email: String,
    val password: String? = null,
    val role: String,
    val firstName: String,
    val lastName: String,
    val phoneNumber: String? = null
)