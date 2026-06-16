package biali.fitmanager.backend.service;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import biali.fitmanager.backend.dto.ChartDataResponse;
import biali.fitmanager.backend.model.AppUser;
import biali.fitmanager.backend.model.Membership;
import biali.fitmanager.backend.model.MembershipType;
import biali.fitmanager.backend.model.Payment;
import biali.fitmanager.backend.repository.AppUserRepository;
import biali.fitmanager.backend.repository.MembershipRepository;
import biali.fitmanager.backend.repository.PaymentRepository;
import biali.fitmanager.backend.repository.TrainerClientRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("Testy generowania raportu PDF")
class PdfReportServiceTest {

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private TrainerClientRepository trainerClientRepository;

    @Mock
    private ChartService chartService;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private MembershipRepository membershipRepository;

    private PdfReportService pdfReportService;

    @BeforeEach
    void setUp() {
        pdfReportService = new PdfReportService(
                appUserRepository,
                trainerClientRepository,
                chartService,
                paymentRepository,
                membershipRepository
        );
    }

    @Test
    @DisplayName("Generuje niepusty plik PDF z poprawnym naglowkiem")
    void generateUsersReportReturnsPdfBytes() {
        AppUser admin = buildUser(1, "ADMIN", "Olaf", "Slowik", "admin@fitmanager.pl");
        AppUser client = buildUser(2, "CLIENT", "Anna", "Nowak", "klient@fitmanager.pl");

        Membership activeMembership = buildMembership(2, "ACTIVE",
                LocalDate.now().minusDays(5), LocalDate.now().plusDays(10), "Monthly pass");

        ChartDataResponse chartData = buildChartData(1, 1);

        when(appUserRepository.findAll()).thenReturn(List.of(admin, client));
        when(membershipRepository.findAll()).thenReturn(List.of(activeMembership));
        when(trainerClientRepository.findAll()).thenReturn(List.of());
        when(chartService.getChartData()).thenReturn(chartData);
        when(paymentRepository.findAll()).thenReturn(List.of());

        byte[] pdf = pdfReportService.generateUsersReport("admin@fitmanager.pl");

        assertNotNull(pdf);
        assertTrue(pdf.length > 100);
        assertTrue(pdf[0] == '%' && pdf[1] == 'P' && pdf[2] == 'D' && pdf[3] == 'F');
    }

    @Test
    @DisplayName("Generuje PDF gdy brak karnetow i platnosci")
    void generateUsersReportWorksWithEmptyMembershipsAndPayments() {
        AppUser admin = buildUser(1, "ADMIN", "Olaf", "Slowik", "admin@fitmanager.pl");

        ChartDataResponse chartData = new ChartDataResponse(
                List.of(new ChartDataResponse.DailyRevenue("2026-06-01", BigDecimal.ZERO, 0)),
                new ChartDataResponse.MembershipStats(0, 0, 0, BigDecimal.ZERO),
                new ChartDataResponse.UserStats(0, 0, 0, 0),
                List.of()
        );

        when(appUserRepository.findAll()).thenReturn(List.of(admin));
        when(membershipRepository.findAll()).thenReturn(List.of());
        when(trainerClientRepository.findAll()).thenReturn(List.of());
        when(chartService.getChartData()).thenReturn(chartData);
        when(paymentRepository.findAll()).thenReturn(List.of());

        byte[] pdf = pdfReportService.generateUsersReport("admin@fitmanager.pl");

        assertNotNull(pdf);
        assertTrue(pdf.length > 100);
        assertTrue(pdf[0] == '%' && pdf[1] == 'P' && pdf[2] == 'D' && pdf[3] == 'F');
    }

    @Test
    @DisplayName("Generuje PDF z wygasajacym karnetem, klientem bez karnetu i ostatnia platnoscia")
    void generateUsersReportIncludesMembershipAndPaymentSections() {
        AppUser clientWithMembership = buildUser(2, "CLIENT", "Anna", "Nowak", "klient@fitmanager.pl");
        AppUser clientWithoutMembership = buildUser(3, "CLIENT", "Jan", "Kowalski", "klient2@fitmanager.pl");

        Membership expiringSoon = buildMembership(2, "ACTIVE",
                LocalDate.now().minusDays(20), LocalDate.now().plusDays(5), "Monthly pass");

        Payment payment = new Payment();
        payment.setUserId(2);
        payment.setAmount(BigDecimal.valueOf(149.99));
        payment.setStatus("SUCCESS");
        ReflectionTestUtils.setField(payment, "id", 1);
        ReflectionTestUtils.setField(payment, "paymentDate", LocalDateTime.now().minusDays(1));

        ChartDataResponse chartData = buildChartData(1, 1);

        when(appUserRepository.findAll()).thenReturn(List.of(clientWithMembership, clientWithoutMembership));
        when(membershipRepository.findAll()).thenReturn(List.of(expiringSoon));
        when(trainerClientRepository.findAll()).thenReturn(List.of());
        when(chartService.getChartData()).thenReturn(chartData);
        when(paymentRepository.findAll()).thenReturn(List.of(payment));

        byte[] pdf = pdfReportService.generateUsersReport("admin@fitmanager.pl");

        assertNotNull(pdf);
        assertTrue(pdf.length > 100);
        assertTrue(pdf[0] == '%' && pdf[1] == 'P' && pdf[2] == 'D' && pdf[3] == 'F');
    }

    private ChartDataResponse buildChartData(int activeMemberships, int clientsWithMembership) {
        return new ChartDataResponse(
                List.of(new ChartDataResponse.DailyRevenue("2026-06-01", BigDecimal.TEN, 1)),
                new ChartDataResponse.MembershipStats(activeMemberships, 0, 0, BigDecimal.valueOf(149.99)),
                new ChartDataResponse.UserStats(1, 0, 0, clientsWithMembership),
                List.of(new ChartDataResponse.MembershipTypeSales("Monthly pass", 1, BigDecimal.valueOf(149.99)))
        );
    }

    private Membership buildMembership(int userId, String status, LocalDate start, LocalDate end, String typeName) {
        MembershipType type = new MembershipType();
        type.setName(typeName);

        Membership membership = new Membership();
        membership.setUserId(userId);
        membership.setStatus(status);
        membership.setStartDate(start);
        membership.setEndDate(end);
        membership.setMembershipType(type);
        return membership;
    }

    private AppUser buildUser(int id, String role, String firstName, String lastName, String email) {
        AppUser user = new AppUser();
        ReflectionTestUtils.setField(user, "id", id);
        user.setRole(role);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setEmail(email);
        user.setAccountBalance(BigDecimal.ZERO);
        return user;
    }
}