package biali.fitmanager.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Query

interface FitManagerApi {
    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @GET("api/me")
    suspend fun getMe(): Response<MeResponse>

    @GET("api/admin/users")
    suspend fun getUsers(@Query("role") role: String? = null): Response<List<UserResponse>>

    @POST("api/admin/users")
    suspend fun createUser(@Body request: UserUpsertRequest): Response<UserResponse>

    @PUT("api/admin/users/{id}")
    suspend fun updateUser(@Path("id") id: Int, @Body request: UserUpsertRequest): Response<UserResponse>

    @DELETE("api/admin/users/{id}")
    suspend fun deleteUser(@Path("id") id: Int): Response<Void>

    @GET("api/admin/trainers")
    suspend fun getTrainers(): Response<List<UserResponse>>

    @POST("api/admin/trainers")
    suspend fun createTrainer(@Body request: UserUpsertRequest): Response<UserResponse>

    @PUT("api/admin/trainers/{id}")
    suspend fun updateTrainer(@Path("id") id: Int, @Body request: UserUpsertRequest): Response<UserResponse>

    @DELETE("api/admin/trainers/{id}")
    suspend fun deleteTrainer(@Path("id") id: Int): Response<Void>

    @GET("api/admin/trainers/{trainerId}/clients")
    suspend fun getTrainerClients(@Path("trainerId") trainerId: Int): Response<List<UserResponse>>

    @GET("api/trainer/me/clients")
    suspend fun getMyTrainerClients(): Response<List<UserResponse>>

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

    @GET("api/membership-types")
    suspend fun getMembershipTypes(): Response<List<MembershipTypeResponse>>

    @POST("api/memberships")
    suspend fun purchaseMembership(@Body request: PurchaseMembershipRequest): Response<MembershipResponse>
}