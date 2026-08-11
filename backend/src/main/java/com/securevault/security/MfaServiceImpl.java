package com.securevault.security;

import com.securevault.common.exception.MfaInvalidException;
import com.securevault.security.crypto.AesEncryptionService;
import com.securevault.security.dto.MfaSetupResponse;
import com.securevault.security.dto.MfaVerifyResponse;
import com.securevault.user.User;
import com.securevault.user.UserRepository;
import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.exceptions.QrGenerationException;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.qr.QrGenerator;
import dev.samstevens.totp.qr.ZxingPngQrGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.util.Utils;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * TOTP (RFC 6238) via dev.samstevens.totp (D-17-adjacent — a maintained, purpose-built library
 * rather than hand-rolling HMAC-based OTP, per P5.4 step 1's explicit instruction). Never logs a
 * secret, code, or backup code — same "never log a secret" rule as everywhere else (master §9).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MfaServiceImpl implements MfaService {

    private static final String ISSUER = "SecureVault";
    private static final int BACKUP_CODE_COUNT = 10;
    // 3 periods (90s) covers a code's full validity window under ±1 allowed discrepancy, so a
    // replayed code is rejected for exactly as long as it would otherwise still verify.
    private static final Duration REPLAY_TTL = Duration.ofSeconds(90);
    private static final String REPLAY_KEY_PREFIX = "mfa:used:";

    private final UserRepository userRepository;
    private final AesEncryptionService aesEncryptionService;
    private final MfaBackupCodeRepository mfaBackupCodeRepository;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redisTemplate;

    private final SecretGenerator secretGenerator = new DefaultSecretGenerator();
    private final QrGenerator qrGenerator = new ZxingPngQrGenerator();
    private final CodeVerifier codeVerifier = buildCodeVerifier();

    private static CodeVerifier buildCodeVerifier() {
        DefaultCodeVerifier verifier =
                new DefaultCodeVerifier(new DefaultCodeGenerator(), new SystemTimeProvider());
        verifier.setTimePeriod(30);
        // ±1 time-step window for clock skew (P5.4 step 4), explicit rather than relying on the
        // library's own default.
        verifier.setAllowedTimePeriodDiscrepancy(1);
        return verifier;
    }

    @Override
    @Transactional
    public MfaSetupResponse setup(Long userId) {
        User user = userRepository.getReferenceById(userId);
        String secret = secretGenerator.generate();
        // Stored AES-encrypted, not enabled yet — mfaEnabled only flips true after verify()
        // confirms the user actually captured the secret correctly (P5.4 step 1/2).
        user.setMfaSecret(aesEncryptionService.encrypt(secret));
        userRepository.save(user);

        QrData qrData =
                new QrData.Builder()
                        .label(user.getEmail())
                        .secret(secret)
                        .issuer(ISSUER)
                        .algorithm(HashingAlgorithm.SHA1)
                        .digits(6)
                        .period(30)
                        .build();

        String qrCodeDataUri;
        try {
            byte[] imageData = qrGenerator.generate(qrData);
            qrCodeDataUri = Utils.getDataUriForImage(imageData, qrGenerator.getImageMimeType());
        } catch (QrGenerationException ex) {
            throw new IllegalStateException("Failed to generate MFA QR code", ex);
        }

        log.info("MFA setup initiated: userId={}", userId);
        return new MfaSetupResponse(secret, qrData.getUri(), qrCodeDataUri);
    }

    @Override
    @Transactional
    public MfaVerifyResponse verify(Long userId, String code) {
        User user = userRepository.getReferenceById(userId);
        if (user.getMfaSecret() == null) {
            throw new MfaInvalidException();
        }
        String secret = aesEncryptionService.decrypt(user.getMfaSecret());
        if (!codeVerifier.isValidCode(secret, code)) {
            throw new MfaInvalidException();
        }

        user.setMfaEnabled(true);
        userRepository.save(user);

        // Fresh set every time verify() is (re-)called — e.g. re-enabling after a disable — old
        // codes are gone (deleteByUserId) so a leaked/exhausted set can't linger.
        mfaBackupCodeRepository.deleteByUserId(userId);
        List<String> plainCodes = new ArrayList<>(BACKUP_CODE_COUNT);
        for (int i = 0; i < BACKUP_CODE_COUNT; i++) {
            String plain = BackupCodeGenerator.generate();
            plainCodes.add(plain);
            mfaBackupCodeRepository.save(
                    MfaBackupCode.builder()
                            .user(user)
                            .codeHash(passwordEncoder.encode(plain))
                            .used(false)
                            .build());
        }

        log.info("MFA enabled: userId={}", userId);
        return new MfaVerifyResponse(plainCodes);
    }

    @Override
    @Transactional
    public void disable(Long userId, String code) {
        User user = userRepository.getReferenceById(userId);
        if (!verifyLoginCode(user, code)) {
            throw new MfaInvalidException();
        }
        user.setMfaEnabled(false);
        user.setMfaSecret(null);
        userRepository.save(user);
        mfaBackupCodeRepository.deleteByUserId(userId);
        log.info("MFA disabled: userId={}", userId);
    }

    @Override
    @Transactional
    public boolean verifyLoginCode(User user, String code) {
        String trimmed = code == null ? "" : code.trim();
        if (trimmed.matches("\\d{6}")) {
            return verifyTotpCode(user, trimmed);
        }
        return consumeBackupCode(user.getId(), trimmed);
    }

    private boolean verifyTotpCode(User user, String code) {
        if (user.getMfaSecret() == null) {
            return false;
        }
        String replayKey = REPLAY_KEY_PREFIX + user.getId() + ":" + code;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(replayKey))) {
            // Replay protection (P5.4 step 4) — a code already accepted once within its own
            // validity window must not verify a second time, even though isValidCode() alone
            // would happily accept it again.
            log.warn("Rejected replayed MFA code: userId={}", user.getId());
            return false;
        }
        String secret = aesEncryptionService.decrypt(user.getMfaSecret());
        boolean valid = codeVerifier.isValidCode(secret, code);
        if (valid) {
            redisTemplate.opsForValue().set(replayKey, "1", REPLAY_TTL);
        }
        return valid;
    }

    private boolean consumeBackupCode(Long userId, String rawCode) {
        for (MfaBackupCode backupCode : mfaBackupCodeRepository.findByUserIdAndUsedFalse(userId)) {
            if (passwordEncoder.matches(rawCode, backupCode.getCodeHash())) {
                backupCode.setUsed(true);
                mfaBackupCodeRepository.save(backupCode);
                log.info("MFA backup code consumed: userId={}", userId);
                return true;
            }
        }
        return false;
    }
}
