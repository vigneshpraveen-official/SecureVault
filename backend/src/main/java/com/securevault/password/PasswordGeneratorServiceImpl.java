package com.securevault.password;

import com.securevault.password.dto.GenerateRequest;
import com.securevault.password.dto.GenerateResponse;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * SecureRandom only — java.util.Random must never appear anywhere in this codebase (P3.2/M-30,
 * verified by grep each session, see docs/progress.md). Algorithm and the "why not a naive random
 * fill" reasoning are documented in docs/password-policy.md and ADR-014.
 */
@Service
@RequiredArgsConstructor
public class PasswordGeneratorServiceImpl implements PasswordGeneratorService {

    private static final String UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijklmnopqrstuvwxyz";
    private static final String DIGITS = "0123456789";
    private static final String SYMBOLS = "!@#$%^&*()-_=+[]{};:,.<>?";
    private static final String AMBIGUOUS = "lI1O0";

    private final PasswordStrengthService passwordStrengthService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public GenerateResponse generate(GenerateRequest request) {
        List<String> pools = enabledPools(request);
        StringBuilder unionPool = new StringBuilder();
        pools.forEach(unionPool::append);

        // Step 1: one guaranteed character per enabled class — this is what a random fill from
        // the union pool alone cannot promise (docs/password-policy.md §2).
        List<Character> characters = new ArrayList<>(request.length());
        for (String pool : pools) {
            characters.add(pool.charAt(secureRandom.nextInt(pool.length())));
        }
        // Step 2: fill the remainder from the union of every enabled class.
        while (characters.size() < request.length()) {
            characters.add(unionPool.charAt(secureRandom.nextInt(unionPool.length())));
        }
        // Step 3: Fisher-Yates shuffle so the guaranteed characters aren't always at the front.
        fisherYatesShuffle(characters);

        StringBuilder password = new StringBuilder(characters.size());
        characters.forEach(password::append);
        String generated = password.toString();

        return new GenerateResponse(generated, passwordStrengthService.analyze(generated));
    }

    private List<String> enabledPools(GenerateRequest request) {
        List<String> pools = new ArrayList<>(4);
        boolean excludeAmbiguous = Boolean.TRUE.equals(request.excludeAmbiguous());
        if (Boolean.TRUE.equals(request.includeUppercase())) {
            pools.add(stripAmbiguous(UPPER, excludeAmbiguous));
        }
        if (Boolean.TRUE.equals(request.includeLowercase())) {
            pools.add(stripAmbiguous(LOWER, excludeAmbiguous));
        }
        if (Boolean.TRUE.equals(request.includeNumbers())) {
            pools.add(stripAmbiguous(DIGITS, excludeAmbiguous));
        }
        if (Boolean.TRUE.equals(request.includeSymbols())) {
            pools.add(SYMBOLS);
        }
        // @AtLeastOneCharacterClass already rejects the empty case with 400 before this runs.
        return pools;
    }

    private String stripAmbiguous(String pool, boolean excludeAmbiguous) {
        if (!excludeAmbiguous) {
            return pool;
        }
        StringBuilder stripped = new StringBuilder(pool);
        for (char ambiguous : AMBIGUOUS.toCharArray()) {
            int index = stripped.indexOf(String.valueOf(ambiguous));
            if (index >= 0) {
                stripped.deleteCharAt(index);
            }
        }
        return stripped.toString();
    }

    private void fisherYatesShuffle(List<Character> characters) {
        for (int i = characters.size() - 1; i > 0; i--) {
            int j = secureRandom.nextInt(i + 1);
            Character temp = characters.get(i);
            characters.set(i, characters.get(j));
            characters.set(j, temp);
        }
    }
}
