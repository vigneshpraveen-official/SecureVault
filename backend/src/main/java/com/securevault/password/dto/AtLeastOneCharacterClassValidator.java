package com.securevault.password.dto;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class AtLeastOneCharacterClassValidator
        implements ConstraintValidator<AtLeastOneCharacterClass, GenerateRequest> {

    @Override
    public boolean isValid(GenerateRequest request, ConstraintValidatorContext context) {
        if (request == null) {
            return true;
        }
        return Boolean.TRUE.equals(request.includeUppercase())
                || Boolean.TRUE.equals(request.includeLowercase())
                || Boolean.TRUE.equals(request.includeNumbers())
                || Boolean.TRUE.equals(request.includeSymbols());
    }
}
