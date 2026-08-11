package com.securevault.password;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.securevault.password.dto.PasswordStrengthResponse;
import org.junit.jupiter.api.Test;

class PasswordStrengthServiceImplTest {

    private final PasswordStrengthService service = new PasswordStrengthServiceImpl();

    @Test
    void commonDictionaryPasswordIsVeryWeakWithNoVarietyFeedback() {
        PasswordStrengthResponse result = service.analyze("password");

        assertEquals(0, result.score());
        assertEquals("Very Weak", result.strength());
        assertTrue(
                result.feedback().stream().anyMatch(f -> f.contains("commonly used")),
                "expected a dictionary-hit feedback message");
        assertTrue(
                result.feedback().stream().anyMatch(f -> f.contains("uppercase")),
                "expected a missing-uppercase feedback message (no variety)");
        assertTrue(
                result.feedback().stream().anyMatch(f -> f.contains("number")),
                "expected a missing-number feedback message (no variety)");
    }

    @Test
    void welcome123MatchesMentorsWorkedExample() {
        PasswordStrengthResponse result = service.analyze("Welcome123");

        assertEquals(3, result.score());
        assertEquals("Medium", result.strength());
        assertTrue(
                result.feedback().stream().anyMatch(f -> f.contains("length")),
                "expected feedback about length");
        assertTrue(
                result.feedback().stream().anyMatch(f -> f.contains("special")),
                "expected feedback about special characters");
    }

    @Test
    void twentyCharRandomMixedPasswordIsVeryStrong() {
        PasswordStrengthResponse result = service.analyze("kJ8#mZ2$pQ9!vX5&nR7@wT");

        assertEquals(5, result.score());
        assertEquals("Very Strong", result.strength());
    }

    @Test
    void repeatedCharactersArePenalized() {
        PasswordStrengthResponse withoutRepeat = service.analyze("qX7#mZ2A!");
        PasswordStrengthResponse withRepeat = service.analyze("aaaaaaaa1A!");

        assertTrue(
                withRepeat.score() < withoutRepeat.score() + 1,
                "a run of repeated characters must cost at least one point");
        assertTrue(
                withRepeat.feedback().stream().anyMatch(f -> f.contains("repeating")),
                "expected a repetition feedback message");
    }

    @Test
    void sequentialPatternIsPenalized() {
        PasswordStrengthResponse result = service.analyze("abcd1234");

        assertTrue(
                result.feedback().stream().anyMatch(f -> f.contains("sequential")),
                "expected a sequential-pattern feedback message");
    }

    @Test
    void sameInputAlwaysYieldsTheSameScore() {
        PasswordStrengthResponse first = service.analyze("SomeReasonable!Pass9");
        PasswordStrengthResponse second = service.analyze("SomeReasonable!Pass9");

        assertEquals(first, second);
    }
}
