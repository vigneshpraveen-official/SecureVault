package com.securevault.password.dto;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Class-level constraint on GenerateRequest — "at least one character class enabled" is a
 * cross-field rule Bean Validation's field-level annotations can't express, so it goes through the
 * same @Valid pipeline as everything else instead of a one-off manual check, per P3.2/M-30.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = AtLeastOneCharacterClassValidator.class)
public @interface AtLeastOneCharacterClass {

    String message() default "at least one character class must be enabled";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
