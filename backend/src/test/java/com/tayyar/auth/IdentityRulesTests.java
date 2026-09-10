package com.tayyar.auth;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.tayyar.user.*;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.*;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.*;
import java.util.*;

class IdentityRulesTests {
    @Test
    void normalizesWithoutProviderSpecificRewriting() {
        assertThat(EmailAddress.normalize("  Person+tag@EXAMPLE.COM  "))
                .isEqualTo("person+tag@example.com");
        assertThat(
                        new RegistrationRequest(
                                        "Name", " PERSON@EXAMPLE.COM ", null, "unchanged pass")
                                .email())
                .isEqualTo("person@example.com");
    }

    @Test
    void validatesUtf8PasswordBoundaries() {
        var validator = new PasswordValidator();
        assertThat(validator.isValid("a".repeat(12), null)).isTrue();
        assertThat(validator.isValid("a".repeat(72), null)).isTrue();
        assertThat(validator.isValid("a".repeat(73), null)).isFalse();
        assertThat(validator.isValid("é".repeat(36), null)).isTrue();
        assertThat(validator.isValid("é".repeat(37), null)).isFalse();
        assertThat(validator.isValid("short", null)).isFalse();
        assertThat(validator.isValid(null, null)).isFalse();
    }

    @Test
    void hashesUnknownUserAttemptWithoutRevealingIdentity() {
        var users = mock(UserRepository.class);
        var encoder = mock(PasswordEncoder.class);
        when(encoder.encode(anyString())).thenReturn("dummy");
        when(users.findByEmail("unknown@example.com")).thenReturn(Optional.empty());
        var manager = new IdentityAuthenticationManager(users, encoder, Clock.systemUTC());
        assertThatThrownBy(
                        () ->
                                manager.authenticate(
                                        UsernamePasswordAuthenticationToken.unauthenticated(
                                                "unknown@example.com", "password-value")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid email or password");
        verify(encoder).matches("password-value", "dummy");
    }

    @Test
    void requestRepresentationsDoNotExposeSecrets() {
        assertThat(new LoginRequest("person@example.com", "secret-value").toString())
                .doesNotContain("secret-value", "person@example.com");
        assertThat(
                        new RegistrationRequest("Name", "person@example.com", null, "secret-value")
                                .toString())
                .doesNotContain("secret-value");
    }

    @Test
    void customerFactoryCannotAssignPrivilegedRoles() {
        var user = User.customer(" Name ", "person@example.com", null, "hash", Instant.now());
        assertThat(user.getRoles()).containsExactly(Role.CUSTOMER);
        assertThat(user.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(user.getFullName()).isEqualTo("Name");
    }
}
