package biali.fitmanager.backend.security;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import biali.fitmanager.backend.model.AppUser;
import biali.fitmanager.backend.repository.AppUserRepository;

/**
 * Ładowanie użytkowników z bazy danych dla Spring Security.
 */
@Service
public class DatabaseUserDetailsService implements UserDetailsService {

    private final AppUserRepository appUserRepository;

    public DatabaseUserDetailsService(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    /**
     * Ładuje użytkownika po adresie email.
     *
     * @param email adres email użytkownika
     * @return {@link UserDetails} z hashem hasła i rolą
     * @throws UsernameNotFoundException gdy użytkownik nie istnieje
     */
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        AppUser user = appUserRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));

        return User.withUsername(user.getEmail())
                .password(user.getPasswordHash())
                .authorities(new SimpleGrantedAuthority(toAuthority(user.getRole())))
                .build();
    }

    /**
     * Konwertuje rolę aplikacji na autorytet Spring Security (ROLE_*).
     *
     * @param role rola z bazy (ADMIN, TRAINER, CLIENT)
     * @return nazwa autorytetu (np. ROLE_CLIENT)
     */
    private String toAuthority(String role) {
        if (role == null || role.isBlank()) {
            return "ROLE_CLIENT";
        }
        String normalizedRole = role.toUpperCase();
        return normalizedRole.startsWith("ROLE_") ? normalizedRole : "ROLE_" + normalizedRole;
    }
}
