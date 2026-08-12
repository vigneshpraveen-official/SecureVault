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

    @Test
    void lengthExactlyTwelveDoesNotEarnTheLengthPoint() {
        // ">12" is a strict inequality — exactly 12 characters must NOT score the length point.
        PasswordStrengthResponse twelve = service.analyze("Ab3#Ab3#Ab3#");
        PasswordStrengthResponse thirteen = service.analyze("Ab3#Ab3#Ab3#x");

        assertEquals(
                4, twelve.score(), "12 chars: upper+lower+digit+special only, no length point");
        assertEquals(5, thirteen.score(), "13 chars: length point now applies");
    }

    @Test
    void tworepeatedCharactersInARowIsNotPenalized() {
        // REPEAT_MIN_RUN is 3 — a run of exactly 2 must not trigger the repetition penalty.
        PasswordStrengthResponse result = service.analyze("aaB3#xyz9Q!");

        assertTrue(
                result.feedback().stream().noneMatch(f -> f.contains("repeating")),
                "a run of only 2 identical characters must not be penalized");
    }

    @Test
    void threeRepeatedCharactersIsTheMinimumPenalizedRun() {
        PasswordStrengthResponse result = service.analyze("aaaB3#xyz9Q!");

        assertTrue(
                result.feedback().stream().anyMatch(f -> f.contains("repeating")),
                "a run of exactly 3 identical characters must be penalized");
    }

    @Test
    void threeCharSequentialRunIsNotPenalized() {
        // SEQUENCE_MIN_RUN is 4 — "abc" alone (3 chars) must not trigger the sequence penalty.
        PasswordStrengthResponse result = service.analyze("Zx9#abcQ7!mN");

        assertTrue(
                result.feedback().stream().noneMatch(f -> f.contains("sequential")),
                "a 3-character ascending run must not be penalized");
    }

    @Test
    void descendingSequentialRunIsPenalized() {
        PasswordStrengthResponse result = service.analyze("Zx9#9876Q7!mN");

        assertTrue(
                result.feedback().stream().anyMatch(f -> f.contains("sequential")),
                "a 4-character descending run must be penalized, not just ascending");
    }

    @Test
    void keyboardRowRunIsPenalizedEvenWithoutAsciiAdjacency() {
        // "qwer" is a keyboard-row run but not an ASCII-ordinal-adjacent sequence.
        PasswordStrengthResponse result = service.analyze("Zx9#qwerQ7!mN");

        assertTrue(
                result.feedback().stream().anyMatch(f -> f.contains("sequential")),
                "a keyboard-row run must be penalized");
    }

    @Test
    void scoreNeverGoesBelowZeroEvenWithEveryPenaltyStacked() {
        // "password" is dictionary-listed, all-lowercase (no variety points), no penalizable
        // repeat/sequence — score floors at 0, never negative.
        PasswordStrengthResponse result = service.analyze("password");

        assertEquals(0, result.score());
    }

    @Test
    void entropyBitsIsPositiveForANonEmptyPassword() {
        PasswordStrengthResponse result = service.analyze("Zx9#qwerQ7!mN");

        assertTrue(result.entropyBits() > 0, "a non-empty password must have positive entropy");
    }

    @Test
    void labelForScoreCoversEveryBand() {
        assertEquals("Very Weak", service.labelForScore(0));
        assertEquals("Weak", service.labelForScore(1));
        assertEquals("Weak", service.labelForScore(2));
        assertEquals("Medium", service.labelForScore(3));
        assertEquals("Strong", service.labelForScore(4));
        assertEquals("Very Strong", service.labelForScore(5));
    }
}
