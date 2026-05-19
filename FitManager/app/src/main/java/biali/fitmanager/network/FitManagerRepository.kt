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

    suspend fun register(request: RegisterRequest): ApiResult<LoginResponse> =
        executeBodyCall { api.register(request) }

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

    suspend fun getAllTrainers(): ApiResult<List<UserResponse>> =
        executeBodyCall { api.getAllTrainers() }

    suspend fun getTrainerById(id: Int): ApiResult<UserResponse> =
        executeBodyCall { api.getTrainerById(id) }

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

    suspend fun getMyMembership(): ApiResult<MembershipResponse> =
        executeBodyCall { api.getMyMembership() }

    suspend fun getMembershipTypes(): ApiResult<List<MembershipTypeResponse>> =
        executeBodyCall { api.getMembershipTypes() }

    suspend fun createMembershipType(request: MembershipTypeUpsertRequest): ApiResult<MembershipTypeResponse> =
        executeBodyCall { api.createMembershipType(request) }

    suspend fun updateMembershipType(id: Int, request: MembershipTypeUpsertRequest): ApiResult<MembershipTypeResponse> =
        executeBodyCall { api.updateMembershipType(id, request) }

    suspend fun deleteMembershipType(id: Int): ApiResult<Unit> =
        executeVoidCall { api.deleteMembershipType(id) }

    suspend fun purchaseMembership(request: PurchaseMembershipRequest): ApiResult<MembershipResponse> =
        safeCall {
            val primaryResponse = api.purchaseMembership(request)
            if (primaryResponse.code() == 405) {
                executeBodyResponse(api.purchaseMembershipForUser(request.userId, request.membershipTypeId))
            } else {
                executeBodyResponse(primaryResponse)
            }
        }

    suspend fun topUpUser(userId: Int, request: TopUpRequest): ApiResult<Unit> =
        executeVoidCall { api.topUpUser(userId, request) }

    suspend fun chooseTrainer(trainerId: Int): ApiResult<Unit> =
        executeVoidCall { api.chooseTrainer(trainerId) }

    suspend fun resignTrainer(trainerId: Int): ApiResult<Unit> =
        executeVoidCall { api.resignTrainer(trainerId) }
    suspend fun getProgressSummary(): ApiResult<ProgressSummaryResponse> =
        executeBodyCall { api.getProgressSummary() }

    suspend fun downloadUsersReportPdf(): ApiResult<okhttp3.ResponseBody> =
        safeCall {
            val response = api.downloadUsersReportPdf()
            executeBodyResponse(response)
        }

    suspend fun getChartData(): ApiResult<ChartDataResponse> =
        executeBodyCall { api.getChartData() }

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

    private fun <T> executeBodyResponse(response: Response<T>): ApiResult<T> {
        return when {
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
        val raw = runCatching { response.errorBody()?.string().orEmpty() }.getOrDefault("")
        if (raw.isBlank()) return null

        // try JSON {"message":"..."}; if backend returns plain text/HTML, show raw body
        val parsed = runCatching { gson.fromJson(raw, ErrorResponse::class.java)?.message }.getOrNull()
        return parsed?.takeIf { it.isNotBlank() } ?: raw
    }

    private fun defaultErrorMessage(code: Int): String = when (code) {
        400 -> "Niepoprawne dane wejściowe."
        401 -> "Brak autoryzacji."
        403 -> "Brak uprawnień."
        404 -> "Nie znaleziono zasobu."
        405 -> "Niedozwolona metoda HTTP (405). Sprawdź endpoint zakupu i metodę POST."
        409 -> "Konflikt danych."
        else -> "Wystąpił błąd serwera ($code)."
    }
}
