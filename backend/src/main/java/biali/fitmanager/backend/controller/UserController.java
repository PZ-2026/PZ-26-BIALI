package biali.fitmanager.backend.controller;

import java.util.List;
import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import biali.fitmanager.backend.dto.AuthErrorResponse;
import biali.fitmanager.backend.dto.UserResponse;
import biali.fitmanager.backend.dto.UserUpsertRequest;
import biali.fitmanager.backend.dto.TopUpRequest;
import biali.fitmanager.backend.model.AppUser;
import biali.fitmanager.backend.model.Membership;
import biali.fitmanager.backend.model.MembershipType;
import biali.fitmanager.backend.repository.AppUserRepository;
import biali.fitmanager.backend.repository.MembershipRepository;
import biali.fitmanager.backend.repository.MembershipTypeRepository;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final MembershipTypeRepository membershipTypeRepository;
    private final MembershipRepository membershipRepository;

    public UserController(AppUserRepository appUserRepository,
                          PasswordEncoder passwordEncoder,
                          MembershipTypeRepository membershipTypeRepository,
                          MembershipRepository membershipRepository) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.membershipTypeRepository = membershipTypeRepository;
        this.membershipRepository = membershipRepository;
    }

    @GetMapping
    public List<UserResponse> getUsers() {
        return appUserRepository.findAll().stream().map(this::toResponse).toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getUserById(@PathVariable Integer id) {
        return appUserRepository.findById(id)
                .<ResponseEntity<?>>map(user -> ResponseEntity.ok(toResponse(user)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new AuthErrorResponse("User not found")));
    }

    @PostMapping
    public ResponseEntity<?> createUser(@RequestBody UserUpsertRequest request) {
        if (!isValidForCreate(request)) {
            return ResponseEntity.badRequest().body(new AuthErrorResponse("Invalid user payload"));
        }

        if (appUserRepository.existsByEmail(request.getEmail())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new AuthErrorResponse("Email already exists"));
        }

        AppUser user = new AppUser();
        applyUpsert(user, request, true);
        AppUser saved = appUserRepository.save(user);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(saved));
    }

    @PostMapping("/{id}/topup")
    public ResponseEntity<?> topUp(@PathVariable Integer id, @RequestBody TopUpRequest request, org.springframework.security.core.Authentication authentication) {
        if (request == null || request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return ResponseEntity.badRequest().body(new AuthErrorResponse("Invalid amount"));
        }
        var authEmail = authentication.getName();
        var authUserOpt = appUserRepository.findByEmail(authEmail);
        if (authUserOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new AuthErrorResponse("Unauthorized"));
        }

        var authUser = authUserOpt.get();
        if (!"ADMIN".equalsIgnoreCase(authUser.getRole()) && !authUser.getId().equals(id)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new AuthErrorResponse("Forbidden"));
        }

        return appUserRepository.findById(id)
                .<ResponseEntity<?>>map(user -> {
                    BigDecimal newBalance = user.getAccountBalance().add(request.getAmount());
                    user.setAccountBalance(newBalance);
                    appUserRepository.save(user);
                    return ResponseEntity.ok(toResponse(user));
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new AuthErrorResponse("User not found")));
    }

    @PostMapping("/{id}/purchase-membership/{membershipTypeId}")
    public ResponseEntity<?> purchaseMembership(@PathVariable Integer id, @PathVariable Integer membershipTypeId, org.springframework.security.core.Authentication authentication) {
        var authEmail = authentication.getName();
        var authUserOpt = appUserRepository.findByEmail(authEmail);
        if (authUserOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new AuthErrorResponse("Unauthorized"));
        }

        var authUser = authUserOpt.get();
        if (!"ADMIN".equalsIgnoreCase(authUser.getRole()) && !authUser.getId().equals(id)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new AuthErrorResponse("Forbidden"));
        }

        var userOpt = appUserRepository.findById(id);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new AuthErrorResponse("User not found"));
        }
        var mtOpt = membershipTypeRepository.findById(membershipTypeId.longValue());
        if (mtOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new AuthErrorResponse("Membership type not found"));
        }

        AppUser user = userOpt.get();
        MembershipType mt = mtOpt.get();
        BigDecimal price = mt.getPrice();
        if (user.getAccountBalance().compareTo(price) < 0) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new AuthErrorResponse("Insufficient funds"));
        }

        // Deduct and save user
        user.setAccountBalance(user.getAccountBalance().subtract(price));
        appUserRepository.save(user);

        // Create membership
        Membership membership = new Membership();
        membership.setUserId(user.getId());
        membership.setMembershipType(mt);
        var start = LocalDate.now();
        membership.setStartDate(start);
        membership.setEndDate(start.plusDays(mt.getDurationDays()));
        membership.setStatus("ACTIVE");
        Membership saved = membershipRepository.save(membership);

        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateUser(@PathVariable Integer id, @RequestBody UserUpsertRequest request) {
        return appUserRepository.findById(id)
                .<ResponseEntity<?>>map(existing -> {
                    if (request.getEmail() != null
                            && !request.getEmail().equalsIgnoreCase(existing.getEmail())
                            && appUserRepository.existsByEmail(request.getEmail())) {
                        return ResponseEntity.status(HttpStatus.CONFLICT)
                                .body(new AuthErrorResponse("Email already exists"));
                    }

                    applyUpsert(existing, request, false);
                    AppUser saved = appUserRepository.save(existing);
                    return ResponseEntity.ok(toResponse(saved));
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new AuthErrorResponse("User not found")));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable Integer id) {
        if (!appUserRepository.existsById(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new AuthErrorResponse("User not found"));
        }
        appUserRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private void applyUpsert(AppUser user, UserUpsertRequest request, boolean createMode) {
        if (request.getEmail() != null) {
            user.setEmail(request.getEmail().trim().toLowerCase());
        }
        if (request.getRole() != null) {
            user.setRole(request.getRole().trim().toUpperCase());
        }
        if (request.getFirstName() != null) {
            user.setFirstName(request.getFirstName().trim());
        }
        if (request.getLastName() != null) {
            user.setLastName(request.getLastName().trim());
        }
        if (request.getPhoneNumber() != null) {
            user.setPhoneNumber(request.getPhoneNumber().trim());
        }
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        }

        if (!createMode) {
            if (user.getRole() == null) {
                user.setRole("CLIENT");
            }
            return;
        }

        if (user.getRole() == null || user.getRole().isBlank()) {
            user.setRole("CLIENT");
        }
    }

    private boolean isValidForCreate(UserUpsertRequest request) {
        return request != null
                && request.getEmail() != null && !request.getEmail().isBlank()
                && request.getPassword() != null && !request.getPassword().isBlank()
                && request.getFirstName() != null && !request.getFirstName().isBlank()
                && request.getLastName() != null && !request.getLastName().isBlank();
    }

    private UserResponse toResponse(AppUser user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getRole(),
                user.getFirstName(),
                user.getLastName(),
                user.getPhoneNumber(),
                user.getCreatedAt(),
                user.getAccountBalance()
        );
    }
}
