package biali.fitmanager.backend.service;

import org.springframework.stereotype.Service;
import biali.fitmanager.backend.dto.ChartDataResponse;
import biali.fitmanager.backend.model.Payment;
import biali.fitmanager.backend.model.Membership;
import biali.fitmanager.backend.model.AppUser;
import biali.fitmanager.backend.repository.PaymentRepository;
import biali.fitmanager.backend.repository.MembershipRepository;
import biali.fitmanager.backend.repository.AppUserRepository;
import biali.fitmanager.backend.repository.TrainerClientRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Agregacja danych statystycznych do wykresów w panelu admina.
 */
@Service
public class ChartService {

    private final PaymentRepository paymentRepository;
    private final MembershipRepository membershipRepository;
    private final AppUserRepository appUserRepository;
    private final TrainerClientRepository trainerClientRepository;

    public ChartService(PaymentRepository paymentRepository,
                       MembershipRepository membershipRepository,
                       AppUserRepository appUserRepository,
                       TrainerClientRepository trainerClientRepository) {
        this.paymentRepository = paymentRepository;
        this.membershipRepository = membershipRepository;
        this.appUserRepository = appUserRepository;
        this.trainerClientRepository = trainerClientRepository;
    }

    /**
     * Zbiera wszystkie dane wykresów w jednej odpowiedzi.
     *
     * @return {@link ChartDataResponse} z przychodami, karnetami, użytkownikami i sprzedażą typów
     */
    public ChartDataResponse getChartData() {
        // Get all data
        List<ChartDataResponse.DailyRevenue> revenueTrend = getRevenueTrend();
        ChartDataResponse.MembershipStats membershipStats = getMembershipStats();
        ChartDataResponse.UserStats userStats = getUserStats();
        List<ChartDataResponse.MembershipTypeSales> membershipTypeSales = getMembershipTypeSales();

        return new ChartDataResponse(revenueTrend, membershipStats, userStats, membershipTypeSales);
    }

    /**
     * Oblicza dzienny przychód z ostatnich 30 dni.
     *
     * @return lista {@link ChartDataResponse.DailyRevenue}
     */
    private List<ChartDataResponse.DailyRevenue> getRevenueTrend() {
        LocalDate thirtyDaysAgo = LocalDate.now().minusDays(30);
        List<Payment> payments = paymentRepository.findAll().stream()
                .filter(p -> p.getPaymentDate() != null && p.getPaymentDate().toLocalDate().isAfter(thirtyDaysAgo))
                .filter(p -> "SUCCESS".equalsIgnoreCase(p.getStatus()))
                .collect(Collectors.toList());

        // Group by date
        Map<LocalDate, List<Payment>> groupedByDate = payments.stream()
                .collect(Collectors.groupingBy(p -> p.getPaymentDate().toLocalDate()));

        // Create daily revenue list
        List<ChartDataResponse.DailyRevenue> result = new ArrayList<>();
        for (LocalDate date = thirtyDaysAgo; !date.isAfter(LocalDate.now()); date = date.plusDays(1)) {
            List<Payment> dayPayments = groupedByDate.getOrDefault(date, new ArrayList<>());
            BigDecimal dayRevenue = dayPayments.stream()
                    .map(Payment::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            result.add(new ChartDataResponse.DailyRevenue(
                    date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
                    dayRevenue,
                    dayPayments.size()
            ));
        }

        return result;
    }

    /**
     * Zlicza karnety według statusu i łączny przychód.
     *
     * @return {@link ChartDataResponse.MembershipStats}
     */
    private ChartDataResponse.MembershipStats getMembershipStats() {
        List<Membership> memberships = membershipRepository.findAll();
        
        int active = (int) memberships.stream().filter(m -> "ACTIVE".equalsIgnoreCase(m.getStatus())).count();
        int expired = (int) memberships.stream().filter(m -> "EXPIRED".equalsIgnoreCase(m.getStatus())).count();
        int cancelled = (int) memberships.stream().filter(m -> "CANCELLED".equalsIgnoreCase(m.getStatus())).count();

        // Calculate total revenue from memberships
        BigDecimal totalRevenue = memberships.stream()
                .filter(m -> m.getMembershipType() != null && m.getMembershipType().getPrice() != null)
                .map(m -> m.getMembershipType().getPrice())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new ChartDataResponse.MembershipStats(active, expired, cancelled, totalRevenue);
    }

    /**
     * Zlicza klientów, trenerów i aktywne przypisania.
     *
     * @return {@link ChartDataResponse.UserStats}
     */
    private ChartDataResponse.UserStats getUserStats() {
        List<AppUser> allUsers = appUserRepository.findAll();
        
        int totalClients = (int) allUsers.stream().filter(u -> "CLIENT".equalsIgnoreCase(u.getRole())).count();
        int totalTrainers = (int) allUsers.stream().filter(u -> "TRAINER".equalsIgnoreCase(u.getRole())).count();

        // Clients with active trainer (via trainer_rentals)
        long clientsWithTrainer = trainerClientRepository.findAll().stream()
                .map(tc -> tc.getClientId())
                .distinct()
                .count();

        // Clients with active membership
        long clientsWithMembership = membershipRepository.findAll().stream()
                .filter(m -> "ACTIVE".equalsIgnoreCase(m.getStatus()))
                .map(Membership::getUserId)
                .distinct()
                .count();

        return new ChartDataResponse.UserStats(
                totalClients,
                totalTrainers,
                (int) clientsWithTrainer,
                (int) clientsWithMembership
        );
    }

    /**
     * Grupuje sprzedaż karnetów według typu.
     *
     * @return lista {@link ChartDataResponse.MembershipTypeSales}
     */
    private List<ChartDataResponse.MembershipTypeSales> getMembershipTypeSales() {
        List<Membership> memberships = membershipRepository.findAll();

        // Group by membership type
        Map<String, List<Membership>> groupedByType = memberships.stream()
                .filter(m -> m.getMembershipType() != null)
                .collect(Collectors.groupingBy(m -> m.getMembershipType().getName()));

        List<ChartDataResponse.MembershipTypeSales> result = new ArrayList<>();
        for (Map.Entry<String, List<Membership>> entry : groupedByType.entrySet()) {
            List<Membership> membersOfType = entry.getValue();
            BigDecimal totalRevenue = membersOfType.stream()
                    .filter(m -> m.getMembershipType() != null && m.getMembershipType().getPrice() != null)
                    .map(m -> m.getMembershipType().getPrice())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            result.add(new ChartDataResponse.MembershipTypeSales(
                    entry.getKey(),
                    membersOfType.size(),
                    totalRevenue
            ));
        }

        return result;
    }
}
