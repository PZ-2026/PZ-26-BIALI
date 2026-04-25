package biali.fitmanager.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import biali.fitmanager.backend.model.TrainingSession;

public interface TrainingSessionRepository extends JpaRepository<TrainingSession, Integer> {

    List<TrainingSession> findAllByTrainerId(Integer trainerId);
}
