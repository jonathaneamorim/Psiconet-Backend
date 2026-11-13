package com.psiconet.model.dtos.auth;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class StrongPasswordValidator implements ConstraintValidator<StrongPassword, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) return true;
        if (PasswordPolicy.exceedsMaxBytes(value)) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(PasswordPolicy.TOO_LONG_MESSAGE).addConstraintViolation();
            return false;
        }
        return PasswordPolicy.matches(value);
    }
}
