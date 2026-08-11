package com.securevault.password.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * All flags are boxed Boolean with @NotNull rather than primitive boolean — this is a
 * security-relevant config, so a caller must state every class explicitly rather than silently
 * defaulting an omitted field to false (P3.2/M-30).
 */
@AtLeastOneCharacterClass
public record GenerateRequest(
        @NotNull @Min(8) @Max(128) Integer length,
        @NotNull Boolean includeUppercase,
        @NotNull Boolean includeLowercase,
        @NotNull Boolean includeNumbers,
        @NotNull Boolean includeSymbols,
        @NotNull Boolean excludeAmbiguous) {}
