package biali.fitmanager.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import biali.fitmanager.backend.model.Membership;

public interface MembershipRepository extends JpaRepository<Membership, Integer> {

    List<Membership> findAllByUserId(Integer userId);
}
