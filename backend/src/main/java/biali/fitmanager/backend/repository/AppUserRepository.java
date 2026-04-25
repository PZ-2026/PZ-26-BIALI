package biali.fitmanager.backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import biali.fitmanager.backend.model.AppUser;

public interface AppUserRepository extends JpaRepository<AppUser, Integer> {

    Optional<AppUser> findByEmail(String email);

    boolean existsByEmail(String email);

    java.util.List<AppUser> findAllByRole(String role);

    java.util.Optional<AppUser> findByIdAndRole(Integer id, String role);

    boolean existsByIdAndRole(Integer id, String role);

    java.util.List<AppUser> findAllByPasswordHashStartingWith(String prefix);
}
