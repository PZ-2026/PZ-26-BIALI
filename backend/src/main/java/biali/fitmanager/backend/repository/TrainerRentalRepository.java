package biali.fitmanager.backend.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import biali.fitmanager.backend.model.TrainerRental;

public interface TrainerRentalRepository extends JpaRepository<TrainerRental, Integer> {
    boolean existsByClientIdAndStatus(Integer clientId, String status);
    List<TrainerRental> findAllByClientId(Integer clientId);
}
