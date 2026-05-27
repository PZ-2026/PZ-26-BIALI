package biali.fitmanager.backend.repository;

import java.math.BigDecimal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import biali.fitmanager.backend.model.Payment;

public interface PaymentRepository extends JpaRepository<Payment, Integer> {

	@Query(value = "SELECT COALESCE(SUM(p.amount), 0) "
			+ "FROM payments p "
			+ "WHERE p.status = 'SUCCESS' AND EXISTS ("
			+ "    SELECT 1 FROM memberships m "
			+ "    WHERE m.id = p.membership_id "
			+ "       OR (p.membership_id IS NULL AND m.user_id = p.user_id AND p.payment_date::date BETWEEN m.start_date AND m.end_date)"
			+ ")", nativeQuery = true)
	BigDecimal sumSuccessfulMembershipRevenue();

	@Query(value = "SELECT COALESCE(SUM(p.amount), 0) "
			+ "FROM payments p "
			+ "WHERE p.status = 'SUCCESS' AND EXISTS ("
			+ "    SELECT 1 FROM memberships m "
			+ "    JOIN trainer_clients tc ON tc.client_id = m.user_id "
			+ "    JOIN users t ON t.id = tc.trainer_id "
			+ "    WHERE t.email = :email "
			+ "      AND (m.id = p.membership_id OR (p.membership_id IS NULL AND m.user_id = p.user_id AND p.payment_date::date BETWEEN m.start_date AND m.end_date))"
			+ ")", nativeQuery = true)
	BigDecimal sumSuccessfulMembershipRevenueForTrainerEmail(@Param("email") String email);

	@Query(value = "SELECT COALESCE(SUM(p.amount), 0) "
			+ "FROM payments p "
			+ "WHERE p.status = 'SUCCESS' AND p.membership_id IS NULL AND EXISTS ("
			+ "    SELECT 1 FROM trainer_rentals tr "
			+ "    JOIN users t ON t.id = tr.trainer_id "
			+ "    WHERE t.email = :email "
			+ "      AND tr.client_id = p.user_id "
			+ "      AND p.payment_date::date BETWEEN tr.start_date AND tr.end_date"
			+ ")", nativeQuery = true)
	BigDecimal sumSuccessfulTrainerRentalRevenue(@Param("email") String email);
}
