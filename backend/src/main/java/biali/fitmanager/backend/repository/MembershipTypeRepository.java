package biali.fitmanager.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import biali.fitmanager.backend.model.MembershipType;

public interface MembershipTypeRepository extends JpaRepository<MembershipType, Integer> {
}