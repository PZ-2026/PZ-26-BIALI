package biali.fitmanager.backend.config;

import java.util.List;

import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import biali.fitmanager.backend.model.AppUser;
import biali.fitmanager.backend.repository.AppUserRepository;

@Component
public class DatabasePasswordInitializer implements CommandLineRunner {

    private static final String PLACEHOLDER_PREFIX = "hashed_pwd_";

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;

    public DatabasePasswordInitializer(AppUserRepository appUserRepository, PasswordEncoder passwordEncoder) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        List<AppUser> usersWithPlaceholders = appUserRepository.findAllByPasswordHashStartingWith(PLACEHOLDER_PREFIX);
        if (usersWithPlaceholders.isEmpty()) {
            return;
        }

        for (AppUser user : usersWithPlaceholders) {
            user.setPasswordHash(passwordEncoder.encode(defaultPasswordForRole(user.getRole())));
        }

        appUserRepository.saveAll(usersWithPlaceholders);
    }

    private String defaultPasswordForRole(String role) {
        if (role == null) {
            return "Haslo123";
        }

        return switch (role.toUpperCase()) {
            case "ADMIN" -> "Admin123";
            case "TRAINER" -> "Trener123";
            default -> "Haslo123";
        };
    }
}
