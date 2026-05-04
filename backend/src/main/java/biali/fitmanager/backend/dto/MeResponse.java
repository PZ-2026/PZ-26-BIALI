package biali.fitmanager.backend.dto;
import java.math.BigDecimal;

public record MeResponse(
        Integer id,
        String email,
        String role,
        String firstName,
        String lastName,
        String phoneNumber,
        BigDecimal accountBalance,
        String name,
        Integer trainerId,
        String trainerEndDate
) {
}
