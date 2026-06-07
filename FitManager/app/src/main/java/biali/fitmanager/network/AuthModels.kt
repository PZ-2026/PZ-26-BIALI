package biali.fitmanager.network

import com.google.gson.annotations.SerializedName

data class LoginRequest(
    val email: String,
    val password: String
)

data class LoginResponse(
    val token: String
)

data class RegisterRequest(
    val email: String,
    val password: String,
    val firstName: String,
    val lastName: String,
    val phoneNumber: String? = null
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
    val name: String? = null,
    @SerializedName("accountBalance")
    val balance: Double? = null,
    val trainerId: Int? = null,
    val trainerEndDate: String? = null
)

data class UserUpsertRequest(
    val email: String,
    val password: String? = null,
    val role: String,
    val firstName: String,
    val lastName: String,
    val phoneNumber: String? = null
)

data class MembershipResponse(
    val id: Int,
    val userId: Int,
    val membershipTypeId: Int,
    val startDate: String,
    val endDate: String,
    val membershipType: MembershipTypeResponse
)

data class MembershipTypeResponse(
    val id: Int,
    val name: String,
    val price: Double,
    val durationDays: Int,
    val description: String? = null
)

data class MembershipTypeUpsertRequest(
    val name: String,
    val price: Double,
    val durationDays: Int,
    val description: String? = null
)

data class PurchaseMembershipRequest(
    val userId: Int,
    val membershipTypeId: Int
)

data class TopUpRequest(
    val amount: Double
)

data class ChangePasswordRequest(
    val currentPassword: String,
    val newPassword: String
)

