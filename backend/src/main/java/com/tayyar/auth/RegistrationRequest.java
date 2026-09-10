package com.tayyar.auth;

import jakarta.validation.constraints.*;

public record RegistrationRequest(
        @NotBlank @Size(max = 120) String fullName,
        @NotBlank @Email @Size(max = 254) String email,
        @Pattern(
                        regexp = "^\\+[1-9][0-9]{7,14}$",
                        message = "Phone must use international E.164 format")
                String phone,
        @ValidPassword String password) {
    public RegistrationRequest {
        if (email != null) email = EmailAddress.normalize(email);
    }

    @Override
    public String toString() {
        return "RegistrationRequest[redacted]";
    }
}
