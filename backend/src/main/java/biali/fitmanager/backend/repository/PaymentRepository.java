package biali.fitmanager.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import biali.fitmanager.backend.model.Payment;

public interface PaymentRepository extends JpaRepository<Payment, Integer> {
}
