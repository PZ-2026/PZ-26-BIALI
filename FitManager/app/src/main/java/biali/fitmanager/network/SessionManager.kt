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
            putString(KEY_TOKEN, token)
            putString(KEY_ROLE, resolvedRole)
        }
    }

    fun getToken(): String? = prefs()?.getString(KEY_TOKEN, null)

    fun getRole(): String? = prefs()?.getString(KEY_ROLE, null)

    fun clearSession() {
        prefs()?.edit { clear() }
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

