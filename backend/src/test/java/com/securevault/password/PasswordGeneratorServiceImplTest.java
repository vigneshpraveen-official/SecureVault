package com.securevault.password;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.securevault.password.dto.GenerateRequest;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PasswordGeneratorServiceImplTest {

    private final PasswordGeneratorService service =
            new PasswordGeneratorServiceImpl(new PasswordStrengthServiceImpl());

    @Test
    void thousandGenerationsWithIdenticalConfigProduceDistinctValues() {
        GenerateRequest request = new GenerateRequest(16, true, true, true, true, false);
        Set<String> generated = new HashSet<>();

        for (int i = 0; i < 1000; i++) {
            generated.add(service.generate(request).password());
        }

        assertEquals(1000, generated.size(), "every generation must be distinct");
    }

    @Test
    void everyGeneratedPasswordSatisfiesItsOwnConfiguration() {
        GenerateRequest request = new GenerateRequest(20, true, true, true, true, false);

        for (int i = 0; i < 200; i++) {
            String password = service.generate(request).password();

            assertEquals(20, password.length());
            assertTrue(password.chars().anyMatch(Character::isUpperCase));
            assertTrue(password.chars().anyMatch(Character::isLowerCase));
            assertTrue(password.chars().anyMatch(Character::isDigit));
            assertTrue(
                    password.chars().anyMatch(c -> !Character.isLetterOrDigit(c)),
                    "expected at least one symbol");
        }
    }

    @Test
    void disablingSymbolsNeverYieldsASymbol() {
        GenerateRequest request = new GenerateRequest(24, true, true, true, false, false);

        for (int i = 0; i < 200; i++) {
            String password = service.generate(request).password();
            assertFalse(
                    password.chars().anyMatch(c -> !Character.isLetterOrDigit(c)),
                    "no symbol should appear when includeSymbols is false");
        }
    }

    @Test
    void excludeAmbiguousRemovesTheFiveAmbiguousCharacters() {
        GenerateRequest request = new GenerateRequest(64, true, true, true, true, true);

        for (int i = 0; i < 100; i++) {
            String password = service.generate(request).password();
            assertFalse(password.contains("l"));
            assertFalse(password.contains("I"));
            assertFalse(password.contains("1"));
            assertFalse(password.contains("O"));
            assertFalse(password.contains("0"));
        }
    }

    @Test
    void singleClassOnlyGeneratesFromThatClass() {
        GenerateRequest request = new GenerateRequest(16, false, false, true, false, false);

        String password = service.generate(request).password();

        assertTrue(password.chars().allMatch(Character::isDigit));
    }

    @Test
    void minimumLengthOfEightIsHonoredAndStillGuaranteesEveryEnabledClass() {
        GenerateRequest request = new GenerateRequest(8, true, true, true, true, false);

        String password = service.generate(request).password();

        assertEquals(8, password.length());
        assertTrue(password.chars().anyMatch(Character::isUpperCase));
        assertTrue(password.chars().anyMatch(Character::isLowerCase));
        assertTrue(password.chars().anyMatch(Character::isDigit));
        assertTrue(password.chars().anyMatch(c -> !Character.isLetterOrDigit(c)));
    }

    @Test
    void twoEnabledClassesBothAppearEvenAtTheMinimumLength() {
        // length == number of enabled classes is the tightest case for the guarantee-then-fill
        // step — every slot is claimed by a guaranteed character, none are left for the union fill.
        GenerateRequest request = new GenerateRequest(8, true, false, true, false, false);

        for (int i = 0; i < 50; i++) {
            String password = service.generate(request).password();
            assertTrue(password.chars().anyMatch(Character::isUpperCase));
            assertTrue(password.chars().anyMatch(Character::isDigit));
            assertTrue(password.chars().noneMatch(Character::isLowerCase));
        }
    }

    @Test
    void generatedPasswordCarriesItsOwnStrengthAnalysisFromTheSharedService() {
        GenerateRequest request = new GenerateRequest(20, true, true, true, true, false);

        var response = service.generate(request);

        assertEquals(
                response.strength(),
                new PasswordStrengthServiceImpl().analyze(response.password()));
    }
}
