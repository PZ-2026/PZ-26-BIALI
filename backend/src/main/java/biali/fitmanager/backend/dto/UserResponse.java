package biali.fitmanager.backend.dto;

import java.time.LocalDateTime;

public record UserResponse(
        Integer id,
        String email,
        String role,
        String firstName,
        String lastName,
        String phoneNumber,
        LocalDateTime createdAt
) {
}
