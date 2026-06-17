package biali.fitmanager.network

import android.content.Context
import android.util.Base64
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

object SessionManager {
    private const val PREFS_NAME = "FitManagerPrefs"
    private const val KEY_TOKEN = "JWT_TOKEN"
    private const val KEY_ROLE = "USER_ROLE"
    private const val KEY_BALANCE = "USER_BALANCE"
    private const val KEY_BALANCE_OWNER_ID = "USER_BALANCE_OWNER_ID"
    private const val KEY_PENDING_TRAINER_NAME = "PENDING_TRAINER_NAME"
    private const val KEY_PENDING_TRAINER_ID = "PENDING_TRAINER_ID"
    private const val KEY_PENDING_TRAINER_END_DATE = "PENDING_TRAINER_END_DATE"

    @Volatile
    private var appContext: Context? = null

    fun initialize(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
        }
    }

    fun saveSession(token: String, role: String? = null) {
        val context = requireContext()
        val resolvedRole = role ?: resolveRoleFromToken(token)

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            // Prevent showing previous user's cached balance before fresh /api/me arrives.
            remove(KEY_BALANCE)
            remove(KEY_BALANCE_OWNER_ID)
            remove(KEY_PENDING_TRAINER_NAME)
            remove(KEY_PENDING_TRAINER_ID)
            remove(KEY_PENDING_TRAINER_END_DATE)
            putString(KEY_TOKEN, token)
            putString(KEY_ROLE, resolvedRole)
        }
    }

    fun getToken(): String? = prefs()?.getString(KEY_TOKEN, null)

    fun getRole(): String? = prefs()?.getString(KEY_ROLE, null)

    fun clearSession() {
        prefs()?.edit {
            remove(KEY_TOKEN)
            remove(KEY_ROLE)
            remove(KEY_BALANCE)
            remove(KEY_BALANCE_OWNER_ID)
            remove(KEY_PENDING_TRAINER_NAME)
            remove(KEY_PENDING_TRAINER_ID)
            remove(KEY_PENDING_TRAINER_END_DATE)
        }
    }

    fun saveBalance(amount: Double) {
        val ownerKey = currentBalanceOwnerKey()
        prefs()?.edit {
            putString(KEY_BALANCE, amount.toString())
            if (ownerKey.isNullOrBlank()) {
                remove(KEY_BALANCE_OWNER_ID)
            } else {
                putString(KEY_BALANCE_OWNER_ID, ownerKey)
            }
        }
    }

    fun getBalance(): Double {
        val cached = prefs()?.getString(KEY_BALANCE, null)?.toDoubleOrNull() ?: return 0.0
        val ownerKey = prefs()?.getString(KEY_BALANCE_OWNER_ID, null)
        val currentOwnerKey = currentBalanceOwnerKey()

        if (ownerKey.isNullOrBlank()) {
            return 0.0
        }
        if (ownerKey != currentOwnerKey) {
            return 0.0
        }

        return cached
    }

    fun changeBalanceBy(delta: Double) {
        val new = getBalance() + delta
        saveBalance(new)
    }

    fun savePendingTrainerCooldown(trainerId: Int, trainerName: String, trainerEndDateIso: String) {
        prefs()?.edit {
            putString(KEY_PENDING_TRAINER_ID, trainerId.toString())
            putString(KEY_PENDING_TRAINER_NAME, trainerName)
            putString(KEY_PENDING_TRAINER_END_DATE, trainerEndDateIso)
        }
    }

    fun getPendingTrainerId(): Int? = prefs()?.getString(KEY_PENDING_TRAINER_ID, null)?.toIntOrNull()

    fun getPendingTrainerName(): String? = prefs()?.getString(KEY_PENDING_TRAINER_NAME, null)

    fun getPendingTrainerEndDate(): String? = prefs()?.getString(KEY_PENDING_TRAINER_END_DATE, null)

    fun clearPendingTrainerCooldown() {
        prefs()?.edit {
            remove(KEY_PENDING_TRAINER_ID)
            remove(KEY_PENDING_TRAINER_NAME)
            remove(KEY_PENDING_TRAINER_END_DATE)
        }
    }

    fun isLoggedIn(): Boolean = !getToken().isNullOrBlank()

    fun resolveEmailFromToken(token: String): String? {
        return runCatching {
            decodePayload(token).optString("sub").ifBlank { null }
        }.getOrNull()
    }

    fun resolveDisplayNameFromToken(token: String): String? {
        return runCatching {
            val payload = decodePayload(token)

            val firstName = payload.optString("firstName").ifBlank { payload.optString("given_name") }
            val lastName = payload.optString("lastName").ifBlank { payload.optString("family_name") }
            if (firstName.isNotBlank() && lastName.isNotBlank()) {
                return@runCatching "$firstName $lastName"
            }

            payload.optString("name").ifBlank { null }
        }.getOrNull()
    }

    fun resolveRoleFromToken(token: String): String? {
        return try {
            val json = decodePayload(token)

            normalizeRole(json.optString("role"))
                ?: normalizeRole(json.optString("roles"))
                ?: normalizeRole(json.optString("authorities"))
                ?: normalizeRole(json.optJSONArray("roles")?.optString(0))
                ?: normalizeRole(json.optJSONArray("authorities")?.optString(0))
                ?: extractRoleFromArray(json.optJSONArray("roles"))
                ?: extractRoleFromArray(json.optJSONArray("authorities"))
        } catch (_: Exception) {
            null
        }
    }

    fun resolveUserIdFromToken(token: String): Int? {
        return runCatching {
            val json = decodePayload(token)
            // try common claims names
            val idAny = when {
                json.has("id") -> json.opt("id")
                json.has("userId") -> json.opt("userId")
                json.has("sub") -> json.opt("sub")
                else -> null
            }
            idAny?.toString()?.toIntOrNull()
        }.getOrNull()
    }

    private fun currentBalanceOwnerKey(): String? {
        val token = getToken() ?: return null
        val userId = resolveUserIdFromToken(token)
        if (userId != null) {
            return "id:$userId"
        }

        val email = resolveEmailFromToken(token)
        if (!email.isNullOrBlank()) {
            return "email:${email.lowercase()}"
        }

        return null
    }

    private fun decodePayload(token: String): JSONObject {
        val parts = token.split(".")
        require(parts.size >= 2)
        val decodedBytes = Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        val payload = String(decodedBytes, Charsets.UTF_8)
        return JSONObject(payload)
    }

    private fun extractRoleFromArray(array: JSONArray?): String? {
        return array?.optString(0)?.let(::normalizeRole)
    }

    private fun normalizeRole(rawRole: String?): String? {
        if (rawRole.isNullOrBlank()) return null
        return rawRole.removePrefix("ROLE_").uppercase()
    }

    private fun prefs() = appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun requireContext(): Context {
        return checkNotNull(appContext) {
            "SessionManager.initialize(context) must be called before using session storage."
        }
    }
}

