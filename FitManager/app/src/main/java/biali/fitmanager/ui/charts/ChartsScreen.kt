package biali.fitmanager.ui.charts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import biali.fitmanager.network.ChartDataResponse
import biali.fitmanager.ui.theme.Green80
import biali.fitmanager.ui.theme.LightGreen80
import java.math.BigDecimal

@Composable
fun ChartsSection(
    modifier: Modifier = Modifier,
    chartData: ChartDataResponse?,
    isLoading: Boolean
) {
    if (isLoading) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(Color(0xFFF5F5F5)),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Green80)
        }
        return
    }

    if (chartData == null) {
        return
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFFFAFAFA))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Statystyki",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1B5E20)
        )

        // User Statistics
        chartData.userStats?.let { stats ->
            UserStatsCard(stats)
        }

        // Membership Statistics
        chartData.membershipStats?.let { stats ->
            MembershipStatsCard(stats)
        }

        // Revenue Stats
        if (chartData.revenueTrend?.isNotEmpty() == true) {
            RevenueTrendCard(chartData.revenueTrend)
        }

        // Membership Type Sales
        if (chartData.membershipTypeSales?.isNotEmpty() == true) {
            MembershipTypeSalesCard(chartData.membershipTypeSales)
        }
    }
}

@Composable
fun UserStatsCard(stats: biali.fitmanager.network.UserStats) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Użytkownicy",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = Green80
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatBox("Klienci", stats.totalClients.toString(), Modifier.weight(1f))
                StatBox("Trenerzy", stats.totalTrainers.toString(), Modifier.weight(1f))
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatBox("Z trenerem", stats.clientsWithActiveTrainer.toString(), Modifier.weight(1f))
                StatBox("Z karnetem", stats.clientsWithActiveMembership.toString(), Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun MembershipStatsCard(stats: biali.fitmanager.network.MembershipStats) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Karnety",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = Green80
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatBox("Aktywne", stats.activeMemberships.toString(), Modifier.weight(1f), Color(0xFF4CAF50))
                StatBox("Wygasłe", stats.expiredMemberships.toString(), Modifier.weight(1f), Color(0xFFFF9800))
                StatBox("Anulowane", stats.cancelledMemberships.toString(), Modifier.weight(1f), Color(0xFFF44336))
            }
            
            Text(
                text = "Przychód z karnetów: ${stats.totalRevenueMemberships} zł",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Green80,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
fun RevenueTrendCard(revenueTrend: List<biali.fitmanager.network.DailyRevenue>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Przychód ostatnie 30 dni",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = Green80
            )
            
            val totalRevenue = revenueTrend.sumOf { it.revenue }
            val avgRevenue = if (revenueTrend.isNotEmpty()) totalRevenue.divide(revenueTrend.size.toBigDecimal(), 2, java.math.RoundingMode.HALF_UP) else BigDecimal.ZERO
            val totalPayments = revenueTrend.sumOf { it.paymentCount }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatBox("Razem", "$totalRevenue zł", Modifier.weight(1f))
                StatBox("Średnia", "$avgRevenue zł", Modifier.weight(1f))
                StatBox("Płatności", totalPayments.toString(), Modifier.weight(1f))
            }
            
            // Simple bar chart alternative - list of days
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                revenueTrend.takeLast(7).forEach { day ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = day.date,
                            fontSize = 10.sp,
                            modifier = Modifier.width(70.dp)
                        )
                        Box(
                            modifier = Modifier
                                .height(20.dp)
                                .fillMaxWidth()
                                .background(LightGreen80)
                        )
                        Text(
                            text = "${day.revenue} zł",
                            fontSize = 10.sp,
                            modifier = Modifier.padding(start = 8.dp),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MembershipTypeSalesCard(membershipTypeSales: List<biali.fitmanager.network.MembershipTypeSales>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Sprzedaż karnetów",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = Green80
            )
            
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                membershipTypeSales.forEach { sale ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF5F5F5))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = sale.membershipTypeName,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Sprzedano: ${sale.salesCount}",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                        Text(
                            text = "${sale.totalRevenue} zł",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Green80
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StatBox(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    backgroundColor: Color = LightGreen80
) {
    Box(
        modifier = modifier
            .background(backgroundColor, shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                fontSize = 11.sp,
                color = Color.Gray,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = value,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1B5E20)
            )
        }
    }
}
