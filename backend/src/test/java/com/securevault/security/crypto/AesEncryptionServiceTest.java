package com.securevault.security.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.security.SecureRandom;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class AesEncryptionServiceTest {

    private final AesEncryptionService service = new AesEncryptionService(randomBase64Key());

    @Test
    void encryptingSamePlaintextTwiceProducesDifferentStoredValuesButSameDecryptedResult() {
        String plaintext = "hunter2";

        String first = service.encrypt(plaintext);
        String second = service.encrypt(plaintext);

        assertNotEquals(first, second, "each encryption must use a fresh random IV");
        assertEquals(plaintext, service.decrypt(first));
        assertEquals(plaintext, service.decrypt(second));
    }

    @Test
    void wrongKeyLengthFailsFast() {
        String tooShort = Base64.getEncoder().encodeToString(new byte[16]);
        assertThrows(IllegalStateException.class, () -> new AesEncryptionService(tooShort));
    }

    @Test
    void malformedBase64KeyFailsFast() {
        assertThrows(
                IllegalStateException.class, () -> new AesEncryptionService("not-valid-base64!!"));
    }

    @Test
    void tamperedCiphertextFailsAuthenticationOnDecrypt() {
        String stored = service.encrypt("hunter2");
        String[] parts = stored.split(":", 2);
        // Flip the FIRST base64 character (never the trailing '=' padding, which would fail at
        // the base64-decode step rather than exercising GCM's own auth-tag check) — the tag must
        // reject this, never silently return corrupted plaintext.
        String tamperedCiphertext = flipFirstChar(parts[1]);
        String tampered = parts[0] + ":" + tamperedCiphertext;

        assertThrows(IllegalStateException.class, () -> service.decrypt(tampered));
    }

    @Test
    void tamperedIvFailsAuthenticationOnDecrypt() {
        String stored = service.encrypt("hunter2");
        String[] parts = stored.split(":", 2);
        String tamperedIv = flipFirstChar(parts[0]);
        String tampered = tamperedIv + ":" + parts[1];

        assertThrows(IllegalStateException.class, () -> service.decrypt(tampered));
    }

    @Test
    void malformedStoredFormatWithoutSeparatorThrows() {
        assertThrows(
                IllegalArgumentException.class, () -> service.decrypt("not-the-expected-format"));
    }

    private static String flipFirstChar(String base64) {
        char first = base64.charAt(0);
        char replacement = first == 'A' ? 'B' : 'A';
        return replacement + base64.substring(1);
    }

    private static String randomBase64Key() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }
}
