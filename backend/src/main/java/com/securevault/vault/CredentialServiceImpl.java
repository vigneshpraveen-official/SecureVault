package com.securevault.vault;

import com.securevault.common.audit.AuditAction;
import com.securevault.common.audit.AuditService;
import com.securevault.common.exception.AccessDeniedException;
import com.securevault.common.exception.CredentialNotFoundException;
import com.securevault.common.exception.PasswordReusedException;
import com.securevault.common.response.PagedResponse;
import com.securevault.password.PasswordStrengthService;
import com.securevault.security.crypto.AesEncryptionService;
import com.securevault.user.User;
import com.securevault.user.UserRepository;
import com.securevault.vault.dto.CredentialCreateRequest;
import com.securevault.vault.dto.CredentialDetailResponse;
import com.securevault.vault.dto.CredentialResponse;
import com.securevault.vault.dto.CredentialSummaryResponse;
import com.securevault.vault.dto.CredentialUpdateRequest;
import com.securevault.vault.dto.PasswordHistoryVersionResponse;
import com.securevault.vault.dto.VaultHealthResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CredentialServiceImpl implements CredentialService {

    private static final int STALE_AFTER_DAYS = 90;

    private final CredentialRepository credentialRepository;
    private final UserRepository userRepository;
    private final AesEncryptionService aesEncryptionService;
    private final CredentialMapper credentialMapper;
    private final PasswordStrengthService passwordStrengthService;
    private final AuditService auditService;
    private final PasswordHistoryRepository passwordHistoryRepository;

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
        // Kept synchronous (S4.6 resolution): the create response must reflect the
        // just-computed strengthScore — moving this off-thread would return a stale/missing
        // value. recomputeStrengthForUser(...) below is the genuinely async path, for bulk work.
        credential.setStrengthScore(
                (short) passwordStrengthService.analyze(request.password()).score());
        credential.setPasswordChangedAt(Instant.now());

        Credential saved = credentialRepository.save(credential);
        // Same transaction as the save above — if this throws, the save rolls back with it
        // (P4.1/M-31/M-32; see AuditServiceImpl's forceAuditFailure flag for the rollback proof).
        auditService.record(
                AuditAction.CREATE,
                "CREDENTIAL",
                saved.getId(),
                userId,
                "title=" + saved.getTitle() + ", category=" + saved.getCategory());
        log.info("Credential created: id={}, userId={}", saved.getId(), userId);

        return credentialMapper.toResponse(saved);
    }

    @Override
    public CredentialDetailResponse getByIdForUser(Long id, Long userId) {
        Credential credential = loadOwned(id, userId);
        String decrypted = aesEncryptionService.decrypt(credential.getEncryptedPassword());
        // DEBUG, not INFO (P4.7/M-47) — a single-credential reveal is developer/traffic detail,
        // not a state-changing business event the way create/update/delete are; logging every
        // read at INFO would also be by far the noisiest line in this class. Never logs the
        // decrypted password itself.
        log.debug("Credential read: id={}, userId={}", id, userId);
        return credentialMapper.toDetailResponse(credential, decrypted);
    }

    @Override
    public List<PasswordHistoryVersionResponse> getPasswordHistory(Long id, Long userId) {
        loadOwned(id, userId); // ownership + not-deleted check; result unused, ids are enough
        return passwordHistoryRepository.findVersionsByCredentialId(id);
    }

    @Override
    public PagedResponse<CredentialSummaryResponse> listForUser(
            Long userId,
            int page,
            int size,
            String sortBy,
            String direction,
            Category category,
            String title,
            String username,
            String website) {
        Pageable pageable =
                PageRequest.of(page, size, Sort.by(Sort.Direction.fromString(direction), sortBy));

        Specification<Credential> spec = CredentialSpecifications.ownedByAndNotDeleted(userId);
        if (category != null) {
            spec = spec.and(CredentialSpecifications.hasCategory(category));
        }
        if (title != null && !title.isBlank()) {
            spec = spec.and(CredentialSpecifications.titleContains(title));
        }
        if (username != null && !username.isBlank()) {
            spec = spec.and(CredentialSpecifications.usernameContains(username));
        }
        if (website != null && !website.isBlank()) {
            spec = spec.and(CredentialSpecifications.websiteContains(website));
        }

        Page<Credential> result = credentialRepository.findAll(spec, pageable);
        List<CredentialSummaryResponse> content = toSummaryResponses(result.getContent());

        return new PagedResponse<>(
                content,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.isFirst(),
                result.isLast(),
                result.hasNext());
    }

    @Override
    public List<CredentialSummaryResponse> search(Long userId, String term) {
        return toSummaryResponses(credentialRepository.search(userId, term));
    }

    // P4.4/M-33: ONE aggregate query for however many credentials are being mapped, instead of
    // one COUNT per credential (which a naive `credential.getPasswordHistories().size()` per row
    // would cause). See docs/evidence/milestone-2/n-plus-one.md for the measured before/after.
    private List<CredentialSummaryResponse> toSummaryResponses(List<Credential> credentials) {
        if (credentials.isEmpty()) {
            return List.of();
        }
        List<Long> ids = credentials.stream().map(Credential::getId).toList();
        Map<Long, Long> historyCounts =
                passwordHistoryRepository.countByCredentialIds(ids).stream()
                        .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
        return credentials.stream()
                .map(
                        c ->
                                credentialMapper.toSummaryResponse(
                                        c, historyCounts.getOrDefault(c.getId(), 0L)))
                .toList();
    }

    @Override
    @Transactional
    public CredentialResponse update(Long id, Long userId, CredentialUpdateRequest request) {
        Credential credential = loadOwned(id, userId);

        // Field names only, never values — this becomes the audit "details" string below, and
        // master §9 forbids logging/auditing a decrypted value (a title/category *name* is fine,
        // but building the string from field names rather than request.toString() keeps that
        // guarantee obvious at a glance rather than relying on no DTO field ever being sensitive).
        List<String> changedFields = new ArrayList<>();
        if (request.title() != null) {
            changedFields.add("title");
        }
        if (request.username() != null) {
            changedFields.add("username");
        }
        if (request.websiteUrl() != null) {
            changedFields.add("websiteUrl");
        }
        if (request.notes() != null) {
            changedFields.add("notes");
        }
        if (request.category() != null) {
            changedFields.add("category");
        }

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
                // Reuse prevention (P4.2/M-36) — checked BEFORE any mutation. Throwing here rolls
                // back everything in this @Transactional method, including the title/username/etc
                // fields the mapper already applied above, since nothing has been save()'d yet.
                for (PasswordHistory history :
                        passwordHistoryRepository.findTop5ByCredentialIdOrderByVersionDesc(id)) {
                    String historicalPlaintext =
                            aesEncryptionService.decrypt(history.getEncryptedPassword());
                    if (historicalPlaintext.equals(request.password())) {
                        throw new PasswordReusedException();
                    }
                }

                // Save the CURRENT (about-to-be-overwritten) ciphertext into history first — reuse
                // the existing ciphertext string as-is, no need to re-encrypt it (P4.2).
                int nextVersion =
                        passwordHistoryRepository
                                .findFirstByCredentialIdOrderByVersionDesc(id)
                                .map(h -> h.getVersion() + 1)
                                .orElse(1);
                passwordHistoryRepository.save(
                        PasswordHistory.builder()
                                .credential(credential)
                                .encryptedPassword(credential.getEncryptedPassword())
                                .version(nextVersion)
                                .build());

                credential.setEncryptedPassword(aesEncryptionService.encrypt(request.password()));
                // strengthScore/passwordChangedAt only move when the password actually changed —
                // renaming a credential or changing its category must not reset either.
                // Kept synchronous (not @Async, S4.6): moving it off the request thread would
                // mean this very response no longer reflects the just-computed strengthScore —
                // a real UX regression. S4.6 instead adds a bulk recompute endpoint that
                // genuinely benefits from running off-thread.
                credential.setStrengthScore(
                        (short) passwordStrengthService.analyze(request.password()).score());
                credential.setPasswordChangedAt(Instant.now());
                changedFields.add("password");
            }
        }

        Credential saved = credentialRepository.save(credential);
        auditService.record(
                AuditAction.UPDATE,
                "CREDENTIAL",
                saved.getId(),
                userId,
                "fields changed: "
                        + (changedFields.isEmpty() ? "none" : String.join(", ", changedFields)));
        if (changedFields.contains("password")) {
            log.info("Credential password changed: id={}, userId={}", saved.getId(), userId);
        }
        log.info("Credential updated: id={}, userId={}", saved.getId(), userId);

        return credentialMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public void delete(Long id, Long userId) {
        // Soft delete (P4.3/M-37): loadOwned already excludes already-deleted rows, so this
        // naturally 404s on a credential that's already in the trash rather than re-deleting it.
        Credential credential = loadOwned(id, userId);
        credential.setDeleted(true);
        credential.setDeletedAt(Instant.now());
        credentialRepository.save(credential);
        auditService.record(
                AuditAction.DELETE,
                "CREDENTIAL",
                credential.getId(),
                userId,
                "title=" + credential.getTitle());
        log.info("Credential soft-deleted: id={}, userId={}", id, userId);
    }

    @Override
    @Transactional
    public CredentialResponse restore(Long id, Long userId) {
        Credential credential = loadOwnedAny(id, userId);
        if (!credential.isDeleted()) {
            // No-op rather than 409: master §9's fixed ErrorCode enum has no code for "already
            // active," and adding one is a locked-decision change this session doesn't own.
            // Restore is naturally idempotent — calling it twice is harmless (P4.3, documented
            // in ADR-018). Still 200, still the current state, just nothing changed.
            return credentialMapper.toResponse(credential);
        }
        credential.setDeleted(false);
        credential.setDeletedAt(null);
        Credential saved = credentialRepository.save(credential);
        auditService.record(
                AuditAction.RESTORE,
                "CREDENTIAL",
                saved.getId(),
                userId,
                "title=" + saved.getTitle());
        log.info("Credential restored: id={}, userId={}", saved.getId(), userId);
        return credentialMapper.toResponse(saved);
    }

    @Override
    public List<CredentialSummaryResponse> trash(Long userId) {
        return toSummaryResponses(credentialRepository.findByUserIdAndDeletedTrue(userId));
    }

    @Override
    @Transactional
    public void permanentDelete(Long id, Long userId) {
        Credential credential = loadOwnedAny(id, userId);
        if (!credential.isDeleted()) {
            // Permanent delete only ever operates on trashed items — an active credential is
            // "not found" from this endpoint's perspective, same reasoning as restore's no-op
            // (no locked error code fits "must be in trash first" either).
            throw new CredentialNotFoundException(id);
        }
        long historyRowsDeleted = passwordHistoryRepository.deleteByCredentialId(id);
        String title = credential.getTitle();
        credentialRepository.delete(credential);
        // AuditLog has no FK to credentials (ADR-017) — this row survives the delete above,
        // proving audit logs stay untouched by permanent delete, verified live (P4.3).
        auditService.record(
                AuditAction.PERMANENT_DELETE,
                "CREDENTIAL",
                id,
                userId,
                "title=" + title + ", historyRowsDeleted=" + historyRowsDeleted);
        log.info("Credential permanently deleted: id={}, userId={}", id, userId);
    }

    @Override
    public VaultHealthResponse getHealth(Long userId) {
        List<Credential> credentials = credentialRepository.findByUserIdAndDeletedFalse(userId);

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

    @Override
    @Async("taskExecutor")
    @Transactional
    public void recomputeStrengthForUser(Long userId) {
        // Own transaction, own thread, no security context (P4.6/ADR-020) — userId came in as an
        // explicit parameter from the controller before this method was ever invoked async.
        log.info(
                "[{}] Recomputing password strength for user {}",
                Thread.currentThread().getName(),
                userId);
        List<Credential> credentials = credentialRepository.findByUserIdAndDeletedFalse(userId);
        for (Credential credential : credentials) {
            String plaintext = aesEncryptionService.decrypt(credential.getEncryptedPassword());
            credential.setStrengthScore((short) passwordStrengthService.analyze(plaintext).score());
        }
        credentialRepository.saveAll(credentials);
        log.info(
                "[{}] Recomputed strength for {} credentials (user {})",
                Thread.currentThread().getName(),
                credentials.size(),
                userId);
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

    // Excludes deleted rows — every "normal" operation (get/update/soft-delete/history) uses
    // this, so a soft-deleted credential is 404 everywhere except trash/restore/permanent-delete.
    private Credential loadOwned(Long id, Long userId) {
        Credential credential =
                credentialRepository
                        .findByIdAndDeletedFalse(id)
                        .orElseThrow(() -> new CredentialNotFoundException(id));
        if (!credential.getUser().getId().equals(userId)) {
            throw new AccessDeniedException("You do not have access to this credential");
        }
        return credential;
    }

    // Sees deleted rows too — only restore/permanent-delete use this, since both are only
    // meaningful when the credential might already be in the trash.
    private Credential loadOwnedAny(Long id, Long userId) {
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
