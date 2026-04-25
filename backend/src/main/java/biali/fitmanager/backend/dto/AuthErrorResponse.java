package biali.fitmanager.backend.dto;

public class AuthErrorResponse {
    private String message;

    public AuthErrorResponse(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
