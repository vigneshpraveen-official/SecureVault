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

    private static String randomBase64Key() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }
}
