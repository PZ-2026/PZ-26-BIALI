package biali.fitmanager.backend.repository;

import java.math.BigDecimal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import biali.fitmanager.backend.model.Payment;

/**
 * Repozytorium płatności z zapytaniami agregującymi przychody.
 */
public interface PaymentRepository extends JpaRepository<Payment, Integer> {

	/**
	 * Sumuje udane płatności za karnety (wszystkich użytkowników).
	 *
	 * @return łączna kwota przychodu z karnetów
	 */
	@Query(value = "SELECT COALESCE(SUM(p.amount), 0) "
			+ "FROM payments p "
			+ "WHERE p.status = 'SUCCESS' AND EXISTS ("
			+ "    SELECT 1 FROM memberships m "
			+ "    WHERE m.id = p.membership_id "
			+ "       OR (p.membership_id IS NULL AND m.user_id = p.user_id AND p.payment_date::date BETWEEN m.start_date AND m.end_date)"
			+ ")", nativeQuery = true)
	BigDecimal sumSuccessfulMembershipRevenue();

	/**
	 * Sumuje udane płatności za karnety klientów przypisanych do trenera.
	 *
	 * @param email email trenera
	 * @return łączna kwota przychodu z karnetów jego klientów
	 */
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

	/**
	 * Sumuje udane płatności za wynajem trenera (trainer rentals).
	 *
	 * @param email email trenera
	 * @return łączna kwota przychodu z wynajmów
	 */
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
