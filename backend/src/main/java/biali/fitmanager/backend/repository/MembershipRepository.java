package biali.fitmanager.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import biali.fitmanager.backend.model.Membership;

public interface MembershipRepository extends JpaRepository<Membership, Integer> {

    List<Membership> findAllByUserId(Integer userId);

    boolean existsByUserIdAndStatus(Integer userId, String status);

    Optional<Membership> findFirstByUserIdAndStatusOrderByEndDateDesc(Integer userId, String status);
}
