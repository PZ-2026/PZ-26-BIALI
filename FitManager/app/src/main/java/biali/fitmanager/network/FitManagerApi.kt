package biali.fitmanager.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Streaming
import retrofit2.http.Path
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Query
import biali.fitmanager.ClientProgressLog
import biali.fitmanager.ClientTrainingSession
import biali.fitmanager.ClientExercise
import biali.fitmanager.ClientSessionExercise
import biali.fitmanager.AddSessionExerciseRequest
import biali.fitmanager.ClientWorkoutDto
import biali.fitmanager.LogWorkoutRequest

data class AssignedSessionExerciseDto(val exerciseId: Int, val exerciseName: String, val sets: Int, val reps: Int, val weight: Double)
data class AssignedSessionDto(val id: Int, val title: String, val date: String, val duration: String, val trainerName: String, val status: String, val exercises: List<AssignedSessionExerciseDto>)

data class SetLogDto(val exerciseId: Int, val setNumber: Int, val weight: Double, val reps: Int)
data class CompleteSessionRequest(val logs: List<SetLogDto>)

data class LogWeightRequest(val weight: Double)
data class UpdateProgressNoteRequest(val notes: String)
data class ClientProgressLogDto(val id: Int, val logDate: String, val weight: Double, val notes: String?)

interface FitManagerApi {
    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @POST("api/auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<LoginResponse>

    @GET("api/me")
    suspend fun getMe(): Response<MeResponse>

    @POST("api/me/password")
    suspend fun changeMyPassword(@Body request: ChangePasswordRequest): Response<Void>

    @GET("api/admin/users")
    suspend fun getUsers(@Query("role") role: String? = null): Response<List<UserResponse>>

    @POST("api/admin/users")
    suspend fun createUser(@Body request: UserUpsertRequest): Response<UserResponse>

    @PUT("api/admin/users/{id}")
    suspend fun updateUser(@Path("id") id: Int, @Body request: UserUpsertRequest): Response<UserResponse>

    @PUT("api/users/{id}")
    suspend fun updateOwnProfile(@Path("id") id: Int, @Body request: UserUpsertRequest): Response<UserResponse>

    @DELETE("api/admin/users/{id}")
    suspend fun deleteUser(@Path("id") id: Int): Response<Void>

    @GET("api/admin/trainers")
    suspend fun getTrainers(): Response<List<UserResponse>>

    @GET("api/trainers")
    suspend fun getAllTrainers(): Response<List<UserResponse>>

    @GET("api/trainers/{id}")
    suspend fun getTrainerById(@Path("id") id: Int): Response<UserResponse>

    @POST("api/admin/trainers")
    suspend fun createTrainer(@Body request: UserUpsertRequest): Response<UserResponse>

    @PUT("api/admin/trainers/{id}")
    suspend fun updateTrainer(@Path("id") id: Int, @Body request: UserUpsertRequest): Response<UserResponse>

    @DELETE("api/admin/trainers/{id}")
    suspend fun deleteTrainer(@Path("id") id: Int): Response<Void>

    @GET("api/admin/trainers/{trainerId}/clients")
    suspend fun getTrainerClients(@Path("trainerId") trainerId: Int): Response<List<UserResponse>>

    @GET("api/admin/revenue/memberships")
    suspend fun getGymMembershipRevenue(): Response<Double>

    @GET("api/trainer/me/clients")
    suspend fun getMyTrainerClients(): Response<List<UserResponse>>
    

    @GET("api/trainer/progress")
    suspend fun getTrainerProgressLogs(): retrofit2.Response<List<ClientProgressLog>>

    @GET("api/trainer/sessions")
    suspend fun getTrainerSessions(): retrofit2.Response<List<ClientTrainingSession>>

    @PUT("api/trainer/progress/{id}/notes")
    suspend fun updateProgressNote(@Path("id") id: Int, @Body request: UpdateProgressNoteRequest): Response<Void>

    @POST("api/trainer/sessions")
    suspend fun addTrainerSession(@Body request: biali.fitmanager.CreateSessionRequest): Response<Void>

    @POST("api/trainer/sessions/{sessionId}/send")
    suspend fun sendSessionToClient(@Path("sessionId") sessionId: Int): Response<Void>

    @DELETE("api/trainer/sessions/{sessionId}")
    suspend fun deleteTrainerSession(@Path("sessionId") sessionId: Int): Response<Void>

    @GET("api/trainer/exercises")
    suspend fun getTrainerExercises(): Response<List<ClientExercise>>

    @GET("api/trainer/sessions/exercises")
    suspend fun getAllSessionExercises(): Response<List<ClientSessionExercise>>

    @POST("api/trainer/sessions/{sessionId}/exercises")
    suspend fun addSessionExercise(@Path("sessionId") sessionId: Int, @Body request: AddSessionExerciseRequest): Response<Void>

    @DELETE("api/trainer/sessions/exercises/{id}")
    suspend fun deleteSessionExercise(@Path("id") id: Int): Response<Void>

    @GET("api/trainer/client-workouts")
    suspend fun getTrainerClientWorkouts(): Response<List<ClientWorkoutDto>>

    @GET("api/trainer/revenue")
    suspend fun getMyTrainerRevenue(): Response<Double>

    @GET("api/client/exercises")
    suspend fun getClientExercises(): Response<List<ClientExercise>>

    @GET("api/client/progress")
    suspend fun getClientProgressLogs(): Response<List<ClientProgressLogDto>>

    @POST("api/client/progress")
    suspend fun addClientProgressLog(@Body request: LogWeightRequest): Response<Void>

    @GET("api/client/workouts")
    suspend fun getMyWorkouts(): Response<List<ClientWorkoutDto>>

    @POST("api/client/workouts")
    suspend fun logClientWorkout(@Body request: LogWorkoutRequest): Response<Void>

    @DELETE("api/client/workouts/{id}")
    suspend fun deleteClientWorkout(@Path("id") id: Int): Response<Void>

    @PUT("api/client/workouts/{id}")
    suspend fun updateClientWorkout(@Path("id") id: Int, @Body request: LogWorkoutRequest): Response<Void>

    @GET("api/client/sessions")
    suspend fun getMyAssignedSessions(): Response<List<AssignedSessionDto>>

    @POST("api/client/sessions/{sessionId}/complete")
    suspend fun completeSession(@Path("sessionId") sessionId: Int, @Body request: CompleteSessionRequest): Response<Void>

    @POST("api/admin/trainers/{trainerId}/clients/{clientId}")
    suspend fun assignClientToTrainer(
        @Path("trainerId") trainerId: Int,
        @Path("clientId") clientId: Int
    ): Response<Void>

    @DELETE("api/admin/trainers/{trainerId}/clients/{clientId}")
    suspend fun unassignClientFromTrainer(
        @Path("trainerId") trainerId: Int,
        @Path("clientId") clientId: Int
    ): Response<Void>

    @GET("api/memberships/me")
    suspend fun getMyMembership(): Response<MembershipResponse>

    @GET("api/memberships")
    suspend fun getMemberships(@Query("userId") userId: Int? = null): Response<List<MembershipResponse>>

    @GET("api/membership-types")
    suspend fun getMembershipTypes(): Response<List<MembershipTypeResponse>>

    @POST("api/admin/membership-types")
    suspend fun createMembershipType(@Body request: MembershipTypeUpsertRequest): Response<MembershipTypeResponse>

    @PUT("api/admin/membership-types/{id}")
    suspend fun updateMembershipType(@Path("id") id: Int, @Body request: MembershipTypeUpsertRequest): Response<MembershipTypeResponse>

    @DELETE("api/admin/membership-types/{id}")
    suspend fun deleteMembershipType(@Path("id") id: Int): Response<Void>

    // backend endpoint for membership purchase
    @POST("api/memberships/purchase")
    suspend fun purchaseMembership(@Body request: PurchaseMembershipRequest): Response<MembershipResponse>

    // compatibility endpoint used by older backend variants
    @POST("api/users/{id}/purchase-membership/{membershipTypeId}")
    suspend fun purchaseMembershipForUser(
        @Path("id") id: Int,
        @Path("membershipTypeId") membershipTypeId: Int
    ): Response<MembershipResponse>

    @POST("api/users/{id}/topup")
    suspend fun topUpUser(@Path("id") id: Int, @Body request: TopUpRequest): Response<Void>

    @POST("api/trainers/{trainerId}/choose")
    suspend fun chooseTrainer(@Path("trainerId") trainerId: Int): Response<Void>

    @DELETE("api/trainers/{trainerId}/choose")
    suspend fun resignTrainer(@Path("trainerId") trainerId: Int): Response<Void>

    @GET("api/progress/summary")
    suspend fun getProgressSummary(): retrofit2.Response<ProgressSummaryResponse>

    @Streaming
    @GET("api/admin/reports/users/pdf")
    suspend fun downloadUsersReportPdf(): Response<okhttp3.ResponseBody>

    @GET("api/admin/charts/data")
    suspend fun getChartData(): Response<ChartDataResponse>
}