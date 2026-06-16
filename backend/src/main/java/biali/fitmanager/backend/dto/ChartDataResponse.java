package biali.fitmanager.backend.dto;

import java.math.BigDecimal;
import java.util.List;

public class ChartDataResponse {

    // For revenue trend - daily revenue
    public static class DailyRevenue {
        public String date;
        public BigDecimal revenue;
        public int paymentCount;

        public DailyRevenue(String date, BigDecimal revenue, int paymentCount) {
            this.date = date;
            this.revenue = revenue;
            this.paymentCount = paymentCount;
        }

        public String getDate() { return date; }
        public BigDecimal getRevenue() { return revenue; }
        public int getPaymentCount() { return paymentCount; }
    }

    // For membership statistics
    public static class MembershipStats {
        public int activeMemberships;
        public int expiredMemberships;
        public int cancelledMemberships;
        public BigDecimal totalRevenueMemberships;

        public MembershipStats(int active, int expired, int cancelled, BigDecimal totalRevenue) {
            this.activeMemberships = active;
            this.expiredMemberships = expired;
            this.cancelledMemberships = cancelled;
            this.totalRevenueMemberships = totalRevenue;
        }

        public int getActiveMemberships() { return activeMemberships; }
        public int getExpiredMemberships() { return expiredMemberships; }
        public int getCancelledMemberships() { return cancelledMemberships; }
        public BigDecimal getTotalRevenueMemberships() { return totalRevenueMemberships; }
    }

    // For user statistics
    public static class UserStats {
        public int totalClients;
        public int totalTrainers;
        public int clientsWithActiveTrainer;
        public int clientsWithActiveMembership;

        public UserStats(int totalClients, int totalTrainers, int withTrainer, int withMembership) {
            this.totalClients = totalClients;
            this.totalTrainers = totalTrainers;
            this.clientsWithActiveTrainer = withTrainer;
            this.clientsWithActiveMembership = withMembership;
        }

        public int getTotalClients() { return totalClients; }
        public int getTotalTrainers() { return totalTrainers; }
        public int getClientsWithActiveTrainer() { return clientsWithActiveTrainer; }
        public int getClientsWithActiveMembership() { return clientsWithActiveMembership; }
    }

    // For membership type sales
    public static class MembershipTypeSales {
        public String membershipTypeName;
        public int salesCount;
        public BigDecimal totalRevenue;

        public MembershipTypeSales(String name, int count, BigDecimal revenue) {
            this.membershipTypeName = name;
            this.salesCount = count;
            this.totalRevenue = revenue;
        }

        public String getMembershipTypeName() { return membershipTypeName; }
        public int getSalesCount() { return salesCount; }
        public BigDecimal getTotalRevenue() { return totalRevenue; }
    }

    // Main response object
    private List<DailyRevenue> revenueTrend;
    private MembershipStats membershipStats;
    private UserStats userStats;
    private List<MembershipTypeSales> membershipTypeSales;

    public ChartDataResponse() {
    }

    public ChartDataResponse(List<DailyRevenue> revenueTrend,
                           MembershipStats membershipStats,
                           UserStats userStats,
                           List<MembershipTypeSales> membershipTypeSales) {
        this.revenueTrend = revenueTrend;
        this.membershipStats = membershipStats;
        this.userStats = userStats;
        this.membershipTypeSales = membershipTypeSales;
    }

    public List<DailyRevenue> getRevenueTrend() { return revenueTrend; }
    public void setRevenueTrend(List<DailyRevenue> revenueTrend) { this.revenueTrend = revenueTrend; }

    public MembershipStats getMembershipStats() { return membershipStats; }
    public void setMembershipStats(MembershipStats membershipStats) { this.membershipStats = membershipStats; }

    public UserStats getUserStats() { return userStats; }
    public void setUserStats(UserStats userStats) { this.userStats = userStats; }

    public List<MembershipTypeSales> getMembershipTypeSales() { return membershipTypeSales; }
    public void setMembershipTypeSales(List<MembershipTypeSales> membershipTypeSales) { this.membershipTypeSales = membershipTypeSales; }
}
