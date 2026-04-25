package biali.fitmanager.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import biali.fitmanager.backend.model.Reservation;

public interface ReservationRepository extends JpaRepository<Reservation, Integer> {

    List<Reservation> findAllByUserId(Integer userId);

    List<Reservation> findAllBySessionId(Integer sessionId);

    Optional<Reservation> findByUserIdAndSessionId(Integer userId, Integer sessionId);
}
