package biali.fitmanager.network

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response
import java.io.IOException

class FitManagerRepository(
    private val api: FitManagerApi = RetrofitClient.api
) {
    private val gson = Gson()

    suspend fun login(request: LoginRequest): ApiResult<LoginResponse> =
        executeBodyCall { api.login(request) }

    suspend fun getMe(): ApiResult<MeResponse> =
        executeBodyCall { api.getMe() }

    suspend fun getUsers(role: String? = null): ApiResult<List<UserResponse>> =
        executeBodyCall { api.getUsers(role) }

    suspend fun createUser(request: UserUpsertRequest): ApiResult<UserResponse> =
        executeBodyCall { api.createUser(request) }

    suspend fun updateUser(id: Int, request: UserUpsertRequest): ApiResult<UserResponse> =
        executeBodyCall { api.updateUser(id, request) }

    suspend fun deleteUser(id: Int): ApiResult<Unit> =
        executeVoidCall { api.deleteUser(id) }

    suspend fun getTrainers(): ApiResult<List<UserResponse>> =
        executeBodyCall { api.getTrainers() }

    suspend fun createTrainer(request: UserUpsertRequest): ApiResult<UserResponse> =
        executeBodyCall { api.createTrainer(request) }

    suspend fun updateTrainer(id: Int, request: UserUpsertRequest): ApiResult<UserResponse> =
        executeBodyCall { api.updateTrainer(id, request) }

    suspend fun deleteTrainer(id: Int): ApiResult<Unit> =
        executeVoidCall { api.deleteTrainer(id) }

    suspend fun getTrainerClients(trainerId: Int): ApiResult<List<UserResponse>> =
        executeBodyCall { api.getTrainerClients(trainerId) }

    suspend fun getMyTrainerClients(): ApiResult<List<UserResponse>> =
        executeBodyCall { api.getMyTrainerClients() }

    suspend fun assignClientToTrainer(trainerId: Int, clientId: Int): ApiResult<Unit> =
        executeVoidCall { api.assignClientToTrainer(trainerId, clientId) }

    suspend fun unassignClientFromTrainer(trainerId: Int, clientId: Int): ApiResult<Unit> =
        executeVoidCall { api.unassignClientFromTrainer(trainerId, clientId) }

    private suspend fun <T> executeBodyCall(call: suspend () -> Response<T>): ApiResult<T> {
        return safeCall {
            val response = call()
            when {
                response.code() == 401 -> {
                    SessionManager.clearSession()
                    ApiResult.Unauthorized
                }
                response.isSuccessful -> {
                    val body = response.body()
                    if (body != null) {
                        ApiResult.Success(body)
                    } else {
                        ApiResult.Error("Pusta odpowiedź serwera.", response.code())
                    }
                }
                else -> ApiResult.Error(parseErrorMessage(response) ?: defaultErrorMessage(response.code()), response.code())
            }
        }
    }

    private suspend fun executeVoidCall(call: suspend () -> Response<Void>): ApiResult<Unit> {
        return safeCall {
            val response = call()
            when {
                response.code() == 401 -> {
                    SessionManager.clearSession()
                    ApiResult.Unauthorized
                }
                response.isSuccessful -> ApiResult.Success(Unit)
                else -> ApiResult.Error(parseErrorMessage(response) ?: defaultErrorMessage(response.code()), response.code())
            }
        }
    }

    private suspend fun <T> safeCall(block: suspend () -> ApiResult<T>): ApiResult<T> {
        return withContext(Dispatchers.IO) {
            try {
                block()
            } catch (io: IOException) {
                ApiResult.Error("Błąd połączenia z serwerem.")
            } catch (exception: Exception) {
                ApiResult.Error(exception.message ?: "Nieoczekiwany błąd.")
            }
        }
    }

    private fun <T> parseErrorMessage(response: Response<T>): String? {
        return runCatching {
            val raw = response.errorBody()?.string().orEmpty()
            if (raw.isBlank()) return null
            gson.fromJson(raw, ErrorResponse::class.java)?.message
        }.getOrNull()
    }

    private fun defaultErrorMessage(code: Int): String = when (code) {
        400 -> "Niepoprawne dane wejściowe."
        401 -> "Brak autoryzacji."
        403 -> "Brak uprawnień."
        404 -> "Nie znaleziono zasobu."
        409 -> "Konflikt danych."
        else -> "Wystąpił błąd serwera ($code)."
    }
}

