package biali.fitmanager.backend.dto;

import java.time.LocalDateTime;
import java.math.BigDecimal;

public record UserResponse(
        Integer id,
        String email,
        String role,
        String firstName,
        String lastName,
        String phoneNumber,
        LocalDateTime createdAt,
        BigDecimal accountBalance
) {
}
