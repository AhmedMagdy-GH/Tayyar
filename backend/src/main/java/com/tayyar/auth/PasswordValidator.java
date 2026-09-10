package com.tayyar.auth;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.nio.charset.StandardCharsets;

public class PasswordValidator implements ConstraintValidator<ValidPassword, String> {
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value != null
                && value.codePointCount(0, value.length()) >= 12
                && value.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
}
