package com.tayyar.user;

import java.util.*;

public record UserResponse(
        UUID id,
        String fullName,
        String email,
        String phone,
        AccountStatus status,
        boolean emailVerified,
        Set<Role> roles) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                user.getStatus(),
                user.getEmailVerifiedAt() != null,
                user.getRoles());
    }
}
