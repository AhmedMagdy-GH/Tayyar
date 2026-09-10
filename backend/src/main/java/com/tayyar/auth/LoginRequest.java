package com.tayyar.auth;

import jakarta.validation.constraints.*;

public record LoginRequest(
        @NotBlank @Email @Size(max = 254) String email, @NotBlank @Size(max = 72) String password) {
    public LoginRequest {
        if (email != null) email = EmailAddress.normalize(email);
    }

    @Override
    public String toString() {
        return "LoginRequest[redacted]";
    }
}
