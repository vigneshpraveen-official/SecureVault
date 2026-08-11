package com.securevault.vault;

import com.securevault.common.exception.AccessDeniedException;
import com.securevault.common.exception.CredentialNotFoundException;
import com.securevault.password.PasswordStrengthService;
import com.securevault.security.crypto.AesEncryptionService;
import com.securevault.user.User;
import com.securevault.user.UserRepository;
import com.securevault.vault.dto.CredentialCreateRequest;
import com.securevault.vault.dto.CredentialDetailResponse;
import com.securevault.vault.dto.CredentialResponse;
import com.securevault.vault.dto.CredentialSummaryResponse;
import com.securevault.vault.dto.CredentialUpdateRequest;
import com.securevault.vault.dto.VaultHealthResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CredentialServiceImpl implements CredentialService {

    private static final int STALE_AFTER_DAYS = 90;

    private final CredentialRepository credentialRepository;
    private final UserRepository userRepository;
    private final AesEncryptionService aesEncryptionService;
    private final CredentialMapper credentialMapper;
    private final PasswordStrengthService passwordStrengthService;

    @Override
    @Transactional
    public CredentialResponse create(Long userId, CredentialCreateRequest request) {
        User owner = userRepository.getReferenceById(userId);

        Credential credential = credentialMapper.toEntity(request);
        credential.setUser(owner);
        credential.setEncryptedPassword(aesEncryptionService.encrypt(request.password()));
        if (request.category() != null) {
            credential.setCategory(request.category());
        }
        // TODO(S4.6): move onto the async executor once one exists, for bulk-create paths.
        credential.setStrengthScore(
                (short) passwordStrengthService.analyze(request.password()).score());
        credential.setPasswordChangedAt(Instant.now());

        return credentialMapper.toResponse(credentialRepository.save(credential));
    }

    @Override
    public CredentialDetailResponse getByIdForUser(Long id, Long userId) {
        Credential credential = loadOwned(id, userId);
        String decrypted = aesEncryptionService.decrypt(credential.getEncryptedPassword());
        return credentialMapper.toDetailResponse(credential, decrypted);
    }

    @Override
    public List<CredentialSummaryResponse> listForUser(Long userId, Category category) {
        List<Credential> credentials =
                category == null
                        ? credentialRepository.findByUserId(userId)
                        : credentialRepository.findByUserIdAndCategory(userId, category);
        return credentials.stream().map(credentialMapper::toSummaryResponse).toList();
    }

    @Override
    public List<CredentialSummaryResponse> search(Long userId, String term) {
        return credentialRepository.search(userId, term).stream()
                .map(credentialMapper::toSummaryResponse)
                .toList();
    }

    @Override
    @Transactional
    public CredentialResponse update(Long id, Long userId, CredentialUpdateRequest request) {
        Credential credential = loadOwned(id, userId);

        // title/username/websiteUrl/notes/category: MapStruct copies only non-null request
        // fields onto the entity (NullValuePropertyMappingStrategy.IGNORE) — null means "leave
        // unchanged" (S1.4). Password is excluded from the mapper entirely; it needs
        // decrypt-and-compare, which is business logic, not a mapping concern (P2.1/M-24).
        credentialMapper.updateEntityFromRequest(request, credential);

        if (request.password() != null) {
            // Ciphertext can never be compared directly — GCM uses a fresh random IV every
            // encryption (D-05), so the same plaintext never produces the same stored value
            // twice. The only reliable way to detect "actually changed" is to decrypt the
            // current value and compare plaintext to plaintext.
            String currentPlaintext =
                    aesEncryptionService.decrypt(credential.getEncryptedPassword());
            if (!currentPlaintext.equals(request.password())) {
                credential.setEncryptedPassword(aesEncryptionService.encrypt(request.password()));
                // strengthScore/passwordChangedAt only move when the password actually changed —
                // renaming a credential or changing its category must not reset either.
                // TODO(S4.6): move onto the async executor once one exists, for bulk-update paths.
                credential.setStrengthScore(
                        (short) passwordStrengthService.analyze(request.password()).score());
                credential.setPasswordChangedAt(Instant.now());
            }
        }

        return credentialMapper.toResponse(credentialRepository.save(credential));
    }

    @Override
    @Transactional
    public void delete(Long id, Long userId) {
        Credential credential = loadOwned(id, userId);
        // Hard delete for now — soft delete (deleted/deletedAt flags, M-37..M-39) replaces this
        // in S4.3. Intentional simplification for this session, not an oversight.
        credentialRepository.delete(credential);
    }

    @Override
    public VaultHealthResponse getHealth(Long userId) {
        List<Credential> credentials = credentialRepository.findByUserId(userId);

        int veryWeak = 0;
        int weak = 0;
        int medium = 0;
        int strong = 0;
        int veryStrong = 0;
        int scoredCount = 0;
        int scoreSum = 0;

        // Decrypt-hash-discard: plaintext and hashes exist only as local variables for the
        // duration of this loop, never logged, cached, or returned (P3.3).
        Map<String, Integer> passwordHashCounts = new HashMap<>();
        for (Credential credential : credentials) {
            Short scoreBoxed = credential.getStrengthScore();
            if (scoreBoxed != null) {
                int score = scoreBoxed;
                scoredCount++;
                scoreSum += score;
                switch (passwordStrengthService.labelForScore(score)) {
                    case "Very Weak" -> veryWeak++;
                    case "Weak" -> weak++;
                    case "Medium" -> medium++;
                    case "Strong" -> strong++;
                    default -> veryStrong++;
                }
            }
            String plaintext = aesEncryptionService.decrypt(credential.getEncryptedPassword());
            String hash = sha256Hex(plaintext);
            passwordHashCounts.merge(hash, 1, Integer::sum);
        }

        int reusedPasswordCount =
                passwordHashCounts.values().stream()
                        .filter(count -> count > 1)
                        .mapToInt(c -> c)
                        .sum();

        Instant staleThreshold = Instant.now().minus(STALE_AFTER_DAYS, ChronoUnit.DAYS);
        int staleCredentialCount =
                (int)
                        credentials.stream()
                                .filter(c -> c.getPasswordChangedAt().isBefore(staleThreshold))
                                .count();

        int total = credentials.size();
        int healthScore =
                computeHealthScore(
                        total, scoredCount, scoreSum, reusedPasswordCount, staleCredentialCount);

        return new VaultHealthResponse(
                total,
                veryWeak,
                weak,
                medium,
                strong,
                veryStrong,
                reusedPasswordCount,
                staleCredentialCount,
                healthScore);
    }

    private int computeHealthScore(
            int total,
            int scoredCount,
            int scoreSum,
            int reusedPasswordCount,
            int staleCredentialCount) {
        if (total == 0) {
            return 100;
        }
        double averageScore = scoredCount == 0 ? 0.0 : (double) scoreSum / scoredCount;
        double strengthComponent = (averageScore / 5.0) * 60.0;
        double reusedRatio = (double) reusedPasswordCount / total;
        double uniquenessComponent = (1.0 - reusedRatio) * 25.0;
        double staleRatio = (double) staleCredentialCount / total;
        double freshnessComponent = (1.0 - staleRatio) * 15.0;
        double score = strengthComponent + uniquenessComponent + freshnessComponent;
        return Math.max(0, Math.min(100, (int) Math.round(score)));
    }

    private String sha256Hex(String plaintext) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(plaintext.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private Credential loadOwned(Long id, Long userId) {
        Credential credential =
                credentialRepository
                        .findById(id)
                        .orElseThrow(() -> new CredentialNotFoundException(id));
        if (!credential.getUser().getId().equals(userId)) {
            throw new AccessDeniedException("You do not have access to this credential");
        }
        return credential;
    }
}
