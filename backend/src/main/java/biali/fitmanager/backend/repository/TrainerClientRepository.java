package biali.fitmanager.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import biali.fitmanager.backend.model.TrainerClient;

public interface TrainerClientRepository extends JpaRepository<TrainerClient, Integer> {

    List<TrainerClient> findAllByTrainerId(Integer trainerId);

    Optional<TrainerClient> findByTrainerIdAndClientId(Integer trainerId, Integer clientId);

    boolean existsByTrainerIdAndClientId(Integer trainerId, Integer clientId);

    void deleteByTrainerIdAndClientId(Integer trainerId, Integer clientId);
}
