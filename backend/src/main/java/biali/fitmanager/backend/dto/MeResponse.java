package biali.fitmanager.backend.dto;

public record MeResponse(
        Integer id,
        String email,
        String role,
        String firstName,
        String lastName,
        String phoneNumber,
        String name
) {
}
