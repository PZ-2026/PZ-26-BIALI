package biali.fitmanager.network

import com.google.gson.annotations.SerializedName
import java.math.BigDecimal

data class ChartDataResponse(
    @SerializedName("revenueTrend")
    val revenueTrend: List<DailyRevenue>? = null,

    @SerializedName("membershipStats")
    val membershipStats: MembershipStats? = null,

    @SerializedName("userStats")
    val userStats: UserStats? = null,

    @SerializedName("membershipTypeSales")
    val membershipTypeSales: List<MembershipTypeSales>? = null
)

data class DailyRevenue(
    @SerializedName("date")
    val date: String,

    @SerializedName("revenue")
    val revenue: BigDecimal,

    @SerializedName("paymentCount")
    val paymentCount: Int
)

data class MembershipStats(
    @SerializedName("activeMemberships")
    val activeMemberships: Int,

    @SerializedName("expiredMemberships")
    val expiredMemberships: Int,

    @SerializedName("cancelledMemberships")
    val cancelledMemberships: Int,

    @SerializedName("totalRevenueMemberships")
    val totalRevenueMemberships: BigDecimal
)

data class UserStats(
    @SerializedName("totalClients")
    val totalClients: Int,

    @SerializedName("totalTrainers")
    val totalTrainers: Int,

    @SerializedName("clientsWithActiveTrainer")
    val clientsWithActiveTrainer: Int,

    @SerializedName("clientsWithActiveMembership")
    val clientsWithActiveMembership: Int
)

data class MembershipTypeSales(
    @SerializedName("membershipTypeName")
    val membershipTypeName: String,

    @SerializedName("salesCount")
    val salesCount: Int,

    @SerializedName("totalRevenue")
    val totalRevenue: BigDecimal
)
