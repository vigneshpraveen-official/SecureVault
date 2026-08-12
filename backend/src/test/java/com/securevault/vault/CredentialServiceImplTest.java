package com.securevault.vault;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.securevault.common.audit.AuditAction;
import com.securevault.common.audit.AuditService;
import com.securevault.common.exception.AccessDeniedException;
import com.securevault.common.exception.CredentialNotFoundException;
import com.securevault.common.exception.PasswordReusedException;
import com.securevault.monitoring.VaultAnomalyDetector;
import com.securevault.password.PasswordStrengthService;
import com.securevault.password.dto.PasswordStrengthResponse;
import com.securevault.security.crypto.AesEncryptionService;
import com.securevault.sharing.AccessEvaluator;
import com.securevault.sharing.AccessLevel;
import com.securevault.sharing.CredentialShareRepository;
import com.securevault.user.User;
import com.securevault.user.UserRepository;
import com.securevault.vault.dto.CredentialCreateRequest;
import com.securevault.vault.dto.CredentialDetailResponse;
import com.securevault.vault.dto.CredentialResponse;
import com.securevault.vault.dto.CredentialUpdateRequest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CredentialServiceImplTest {

    private static final Long OWNER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long CREDENTIAL_ID = 100L;

    @Mock private CredentialRepository credentialRepository;
    @Mock private UserRepository userRepository;
    @Mock private AesEncryptionService aesEncryptionService;
    @Mock private CredentialMapper credentialMapper;
    @Mock private PasswordStrengthService passwordStrengthService;
    @Mock private AuditService auditService;
    @Mock private PasswordHistoryRepository passwordHistoryRepository;
    @Mock private AccessEvaluator accessEvaluator;
    @Mock private CredentialShareRepository credentialShareRepository;
    @Mock private VaultAnomalyDetector vaultAnomalyDetector;

    private CredentialServiceImpl service;

    @BeforeEach
    void setUp() {
        service =
                new CredentialServiceImpl(
                        credentialRepository,
                        userRepository,
                        aesEncryptionService,
                        credentialMapper,
                        passwordStrengthService,
                        auditService,
                        passwordHistoryRepository,
                        accessEvaluator,
                        credentialShareRepository,
                        vaultAnomalyDetector);
    }

    private Credential ownedCredential(String encryptedPassword, boolean deleted) {
        User owner = User.builder().id(OWNER_ID).build();
        return Credential.builder()
                .id(CREDENTIAL_ID)
                .user(owner)
                .title("GitHub")
                .encryptedPassword(encryptedPassword)
                .category(Category.OTHER)
                .strengthScore((short) 3)
                .passwordChangedAt(Instant.now())
                .deleted(deleted)
                .build();
    }

    // ---- create ----

    @Test
    void should_encryptAndScoreThePassword_when_creatingACredential() {
        CredentialCreateRequest request =
                new CredentialCreateRequest("GitHub", "dave", "Secret1!", null, null, null);
        when(userRepository.getReferenceById(OWNER_ID))
                .thenReturn(User.builder().id(OWNER_ID).build());
        when(credentialMapper.toEntity(request)).thenReturn(Credential.builder().build());
        when(aesEncryptionService.encrypt("Secret1!")).thenReturn("encrypted-blob");
        when(passwordStrengthService.analyze("Secret1!"))
                .thenReturn(new PasswordStrengthResponse(4, "Strong", 50.0, List.of()));
        when(credentialRepository.save(any(Credential.class)))
                .thenAnswer(
                        invocation -> {
                            Credential c = invocation.getArgument(0);
                            c.setId(CREDENTIAL_ID);
                            return c;
                        });
        when(credentialMapper.toResponse(any(Credential.class)))
                .thenReturn(
                        new CredentialResponse(
                                CREDENTIAL_ID,
                                "GitHub",
                                "dave",
                                null,
                                null,
                                Category.OTHER,
                                false,
                                4,
                                Instant.now(),
                                Instant.now()));

        service.create(OWNER_ID, request);

        ArgumentCaptor<Credential> captor = ArgumentCaptor.forClass(Credential.class);
        verify(credentialRepository).save(captor.capture());
        Credential saved = captor.getValue();
        assertEquals("encrypted-blob", saved.getEncryptedPassword());
        assertEquals((short) 4, saved.getStrengthScore());
        assertNotNull(saved.getPasswordChangedAt());
        verify(auditService)
                .record(
                        eq(AuditAction.CREATE),
                        eq("CREDENTIAL"),
                        eq(CREDENTIAL_ID),
                        eq(OWNER_ID),
                        anyString());
    }

    @Test
    void should_defaultCategoryToOther_when_categoryOmittedOnCreate() {
        CredentialCreateRequest request =
                new CredentialCreateRequest("GitHub", "dave", "Secret1!", null, null, null);
        when(userRepository.getReferenceById(OWNER_ID))
                .thenReturn(User.builder().id(OWNER_ID).build());
        when(credentialMapper.toEntity(request)).thenReturn(Credential.builder().build());
        when(aesEncryptionService.encrypt(anyString())).thenReturn("encrypted-blob");
        when(passwordStrengthService.analyze(anyString()))
                .thenReturn(new PasswordStrengthResponse(4, "Strong", 50.0, List.of()));
        when(credentialRepository.save(any(Credential.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(credentialMapper.toResponse(any(Credential.class)))
                .thenReturn(
                        new CredentialResponse(
                                1L,
                                "GitHub",
                                "dave",
                                null,
                                null,
                                Category.OTHER,
                                false,
                                4,
                                Instant.now(),
                                Instant.now()));

        service.create(OWNER_ID, request);

        ArgumentCaptor<Credential> captor = ArgumentCaptor.forClass(Credential.class);
        verify(credentialRepository).save(captor.capture());
        assertEquals(Category.OTHER, captor.getValue().getCategory());
    }

    @Test
    void should_applyTheExplicitCategory_when_providedOnCreate() {
        CredentialCreateRequest request =
                new CredentialCreateRequest(
                        "Chase", "dave", "Secret1!", null, null, Category.BANKING);
        when(userRepository.getReferenceById(OWNER_ID))
                .thenReturn(User.builder().id(OWNER_ID).build());
        when(credentialMapper.toEntity(request)).thenReturn(Credential.builder().build());
        when(aesEncryptionService.encrypt(anyString())).thenReturn("encrypted-blob");
        when(passwordStrengthService.analyze(anyString()))
                .thenReturn(new PasswordStrengthResponse(4, "Strong", 50.0, List.of()));
        when(credentialRepository.save(any(Credential.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(credentialMapper.toResponse(any(Credential.class)))
                .thenReturn(
                        new CredentialResponse(
                                1L,
                                "Chase",
                                "dave",
                                null,
                                null,
                                Category.BANKING,
                                false,
                                4,
                                Instant.now(),
                                Instant.now()));

        service.create(OWNER_ID, request);

        ArgumentCaptor<Credential> captor = ArgumentCaptor.forClass(Credential.class);
        verify(credentialRepository).save(captor.capture());
        assertEquals(Category.BANKING, captor.getValue().getCategory());
    }

    // ---- getByIdForUser: ownership / sharing enforcement ----

    @Test
    void should_returnTheDecryptedPassword_when_theOwnerReads() {
        Credential credential = ownedCredential("cipher", false);
        when(credentialRepository.findByIdAndDeletedFalse(CREDENTIAL_ID))
                .thenReturn(Optional.of(credential));
        when(accessEvaluator.evaluate(CREDENTIAL_ID, OWNER_ID, OWNER_ID))
                .thenReturn(AccessLevel.OWNER);
        when(aesEncryptionService.decrypt("cipher")).thenReturn("plaintext");
        when(credentialMapper.toDetailResponse(credential, "plaintext"))
                .thenReturn(
                        new CredentialDetailResponse(
                                CREDENTIAL_ID,
                                "GitHub",
                                "dave",
                                "plaintext",
                                null,
                                null,
                                Category.OTHER,
                                false,
                                Instant.now(),
                                Instant.now()));

        CredentialDetailResponse response = service.getByIdForUser(CREDENTIAL_ID, OWNER_ID);

        assertEquals("plaintext", response.password());
        verify(vaultAnomalyDetector).recordAccess(OWNER_ID);
        // Owner reads are logged at DEBUG only, never audited (distinct from the shared-access
        // path).
        verify(auditService, never()).record(eq(AuditAction.ACCESS), any(), any(), any(), any());
    }

    @Test
    void should_auditTheAccess_when_aSharedReadUserReadsSomeoneElsesCredential() {
        Credential credential = ownedCredential("cipher", false);
        when(credentialRepository.findByIdAndDeletedFalse(CREDENTIAL_ID))
                .thenReturn(Optional.of(credential));
        when(accessEvaluator.evaluate(CREDENTIAL_ID, OWNER_ID, OTHER_USER_ID))
                .thenReturn(AccessLevel.READ);
        when(aesEncryptionService.decrypt("cipher")).thenReturn("plaintext");
        when(credentialMapper.toDetailResponse(any(), anyString()))
                .thenReturn(
                        new CredentialDetailResponse(
                                CREDENTIAL_ID,
                                "GitHub",
                                "dave",
                                "plaintext",
                                null,
                                null,
                                Category.OTHER,
                                false,
                                Instant.now(),
                                Instant.now()));

        service.getByIdForUser(CREDENTIAL_ID, OTHER_USER_ID);

        verify(auditService)
                .record(
                        eq(AuditAction.ACCESS),
                        eq("CREDENTIAL"),
                        eq(CREDENTIAL_ID),
                        eq(OTHER_USER_ID),
                        anyString());
    }

    @Test
    void should_denyRead_when_theCallerHasNoOwnershipOrShare() {
        Credential credential = ownedCredential("cipher", false);
        when(credentialRepository.findByIdAndDeletedFalse(CREDENTIAL_ID))
                .thenReturn(Optional.of(credential));
        when(accessEvaluator.evaluate(CREDENTIAL_ID, OWNER_ID, OTHER_USER_ID))
                .thenReturn(AccessLevel.NONE);

        assertThrows(
                AccessDeniedException.class,
                () -> service.getByIdForUser(CREDENTIAL_ID, OTHER_USER_ID));
        verify(aesEncryptionService, never()).decrypt(anyString());
    }

    @Test
    void should_throwNotFound_when_theCredentialDoesNotExist() {
        when(credentialRepository.findByIdAndDeletedFalse(CREDENTIAL_ID))
                .thenReturn(Optional.empty());

        assertThrows(
                CredentialNotFoundException.class,
                () -> service.getByIdForUser(CREDENTIAL_ID, OWNER_ID));
    }

    // ---- update: re-encrypt only on an actual change ----

    @Test
    void should_leaveTheCiphertextUntouched_when_onlyTheTitleChanges() {
        Credential credential = ownedCredential("original-cipher", false);
        CredentialUpdateRequest request =
                new CredentialUpdateRequest("New Title", null, null, null, null, null);
        when(credentialRepository.findByIdAndDeletedFalse(CREDENTIAL_ID))
                .thenReturn(Optional.of(credential));
        when(accessEvaluator.evaluate(CREDENTIAL_ID, OWNER_ID, OWNER_ID))
                .thenReturn(AccessLevel.OWNER);
        when(credentialRepository.save(any(Credential.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(credentialMapper.toResponse(any(Credential.class)))
                .thenReturn(
                        new CredentialResponse(
                                CREDENTIAL_ID,
                                "New Title",
                                null,
                                null,
                                null,
                                Category.OTHER,
                                false,
                                3,
                                Instant.now(),
                                Instant.now()));

        service.update(CREDENTIAL_ID, OWNER_ID, request);

        assertEquals("original-cipher", credential.getEncryptedPassword());
        verify(aesEncryptionService, never()).decrypt(anyString());
        verify(aesEncryptionService, never()).encrypt(anyString());
        verify(passwordHistoryRepository, never()).save(any());
    }

    @Test
    void should_reEncryptAndVersionHistory_when_thePasswordActuallyChanges() {
        Credential credential = ownedCredential("original-cipher", false);
        CredentialUpdateRequest request =
                new CredentialUpdateRequest(null, null, "NewSecret1!", null, null, null);
        when(credentialRepository.findByIdAndDeletedFalse(CREDENTIAL_ID))
                .thenReturn(Optional.of(credential));
        when(accessEvaluator.evaluate(CREDENTIAL_ID, OWNER_ID, OWNER_ID))
                .thenReturn(AccessLevel.OWNER);
        when(aesEncryptionService.decrypt("original-cipher")).thenReturn("OldSecret1!");
        when(passwordHistoryRepository.findTop5ByCredentialIdOrderByVersionDesc(CREDENTIAL_ID))
                .thenReturn(List.of());
        when(passwordHistoryRepository.findFirstByCredentialIdOrderByVersionDesc(CREDENTIAL_ID))
                .thenReturn(Optional.empty());
        when(aesEncryptionService.encrypt("NewSecret1!")).thenReturn("new-cipher");
        when(passwordStrengthService.analyze("NewSecret1!"))
                .thenReturn(new PasswordStrengthResponse(5, "Very Strong", 80.0, List.of()));
        when(credentialRepository.save(any(Credential.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(credentialMapper.toResponse(any(Credential.class)))
                .thenReturn(
                        new CredentialResponse(
                                CREDENTIAL_ID,
                                "GitHub",
                                null,
                                null,
                                null,
                                Category.OTHER,
                                false,
                                5,
                                Instant.now(),
                                Instant.now()));

        service.update(CREDENTIAL_ID, OWNER_ID, request);

        ArgumentCaptor<PasswordHistory> historyCaptor =
                ArgumentCaptor.forClass(PasswordHistory.class);
        verify(passwordHistoryRepository).save(historyCaptor.capture());
        assertEquals("original-cipher", historyCaptor.getValue().getEncryptedPassword());
        assertEquals(1, historyCaptor.getValue().getVersion());
        assertEquals("new-cipher", credential.getEncryptedPassword());
        assertEquals((short) 5, credential.getStrengthScore());
    }

    @Test
    void should_skipReEncryption_when_theNewPasswordDecryptsToTheSameValue() {
        Credential credential = ownedCredential("original-cipher", false);
        CredentialUpdateRequest request =
                new CredentialUpdateRequest(null, null, "SamePassword1!", null, null, null);
        when(credentialRepository.findByIdAndDeletedFalse(CREDENTIAL_ID))
                .thenReturn(Optional.of(credential));
        when(accessEvaluator.evaluate(CREDENTIAL_ID, OWNER_ID, OWNER_ID))
                .thenReturn(AccessLevel.OWNER);
        when(aesEncryptionService.decrypt("original-cipher")).thenReturn("SamePassword1!");
        when(credentialRepository.save(any(Credential.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(credentialMapper.toResponse(any(Credential.class)))
                .thenReturn(
                        new CredentialResponse(
                                CREDENTIAL_ID,
                                "GitHub",
                                null,
                                null,
                                null,
                                Category.OTHER,
                                false,
                                3,
                                Instant.now(),
                                Instant.now()));

        service.update(CREDENTIAL_ID, OWNER_ID, request);

        verify(aesEncryptionService, never()).encrypt(anyString());
        verify(passwordHistoryRepository, never()).save(any());
        assertEquals("original-cipher", credential.getEncryptedPassword());
    }

    @Test
    void should_rejectAndNotPersistAnything_when_theNewPasswordMatchesRecentHistory() {
        Credential credential = ownedCredential("original-cipher", false);
        CredentialUpdateRequest request =
                new CredentialUpdateRequest(null, null, "ReusedPassword1!", null, null, null);
        when(credentialRepository.findByIdAndDeletedFalse(CREDENTIAL_ID))
                .thenReturn(Optional.of(credential));
        when(accessEvaluator.evaluate(CREDENTIAL_ID, OWNER_ID, OWNER_ID))
                .thenReturn(AccessLevel.OWNER);
        when(aesEncryptionService.decrypt("original-cipher")).thenReturn("CurrentPassword1!");
        PasswordHistory historicalMatch =
                PasswordHistory.builder().encryptedPassword("hist-cipher").version(3).build();
        when(passwordHistoryRepository.findTop5ByCredentialIdOrderByVersionDesc(CREDENTIAL_ID))
                .thenReturn(List.of(historicalMatch));
        when(aesEncryptionService.decrypt("hist-cipher")).thenReturn("ReusedPassword1!");

        assertThrows(
                PasswordReusedException.class,
                () -> service.update(CREDENTIAL_ID, OWNER_ID, request));

        verify(credentialRepository, never()).save(any());
        verify(passwordHistoryRepository, never()).save(any());
    }

    @Test
    void should_allowUpdate_when_theCallerHasAnActiveEditShare() {
        Credential credential = ownedCredential("original-cipher", false);
        CredentialUpdateRequest request =
                new CredentialUpdateRequest("New Title", null, null, null, null, null);
        when(credentialRepository.findByIdAndDeletedFalse(CREDENTIAL_ID))
                .thenReturn(Optional.of(credential));
        when(accessEvaluator.evaluate(CREDENTIAL_ID, OWNER_ID, OTHER_USER_ID))
                .thenReturn(AccessLevel.EDIT);
        when(credentialRepository.save(any(Credential.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(credentialMapper.toResponse(any(Credential.class)))
                .thenReturn(
                        new CredentialResponse(
                                CREDENTIAL_ID,
                                "New Title",
                                null,
                                null,
                                null,
                                Category.OTHER,
                                false,
                                3,
                                Instant.now(),
                                Instant.now()));

        service.update(CREDENTIAL_ID, OTHER_USER_ID, request);

        verify(credentialRepository).save(any(Credential.class));
    }

    @Test
    void should_denyUpdate_when_theCallerOnlyHasAReadShare() {
        Credential credential = ownedCredential("original-cipher", false);
        CredentialUpdateRequest request =
                new CredentialUpdateRequest("New Title", null, null, null, null, null);
        when(credentialRepository.findByIdAndDeletedFalse(CREDENTIAL_ID))
                .thenReturn(Optional.of(credential));
        when(accessEvaluator.evaluate(CREDENTIAL_ID, OWNER_ID, OTHER_USER_ID))
                .thenReturn(AccessLevel.READ);

        assertThrows(
                AccessDeniedException.class,
                () -> service.update(CREDENTIAL_ID, OTHER_USER_ID, request));
        verify(credentialRepository, never()).save(any());
    }

    // ---- delete: soft delete, owner-only (a share, even EDIT, must not delete) ----

    @Test
    void should_softDeleteOnly_when_theOwnerDeletes() {
        Credential credential = ownedCredential("cipher", false);
        when(credentialRepository.findByIdAndDeletedFalse(CREDENTIAL_ID))
                .thenReturn(Optional.of(credential));
        when(credentialRepository.save(any(Credential.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.delete(CREDENTIAL_ID, OWNER_ID);

        assertTrue(credential.isDeleted());
        assertNotNull(credential.getDeletedAt());
        verify(credentialRepository, never()).delete(any(Credential.class));
        verify(auditService)
                .record(
                        eq(AuditAction.DELETE),
                        eq("CREDENTIAL"),
                        eq(CREDENTIAL_ID),
                        eq(OWNER_ID),
                        anyString());
    }

    @Test
    void should_denyDelete_when_theCallerIsNotTheOwnerEvenWithAnEditShare() {
        // Delete goes through loadOwned (strict ownership), never loadWithAccess — sharing never
        // grants delete rights, regardless of permission level (M-45).
        Credential credential = ownedCredential("cipher", false);
        when(credentialRepository.findByIdAndDeletedFalse(CREDENTIAL_ID))
                .thenReturn(Optional.of(credential));

        assertThrows(
                AccessDeniedException.class, () -> service.delete(CREDENTIAL_ID, OTHER_USER_ID));
        verify(credentialRepository, never()).save(any());
        verify(accessEvaluator, never()).evaluate(any(), any(), any());
    }

    // ---- restore: idempotent no-op vs. real restore ----

    @Test
    void should_doNothing_when_restoringAnAlreadyActiveCredential() {
        Credential credential = ownedCredential("cipher", false);
        when(credentialRepository.findById(CREDENTIAL_ID)).thenReturn(Optional.of(credential));
        when(credentialMapper.toResponse(credential))
                .thenReturn(
                        new CredentialResponse(
                                CREDENTIAL_ID,
                                "GitHub",
                                null,
                                null,
                                null,
                                Category.OTHER,
                                false,
                                3,
                                Instant.now(),
                                Instant.now()));

        service.restore(CREDENTIAL_ID, OWNER_ID);

        verify(credentialRepository, never()).save(any());
        verify(auditService, never()).record(eq(AuditAction.RESTORE), any(), any(), any(), any());
    }

    @Test
    void should_clearDeletedFlagAndTimestamp_when_restoringATrashedCredential() {
        Credential credential = ownedCredential("cipher", true);
        credential.setDeletedAt(Instant.now());
        when(credentialRepository.findById(CREDENTIAL_ID)).thenReturn(Optional.of(credential));
        when(credentialRepository.save(any(Credential.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(credentialMapper.toResponse(any(Credential.class)))
                .thenReturn(
                        new CredentialResponse(
                                CREDENTIAL_ID,
                                "GitHub",
                                null,
                                null,
                                null,
                                Category.OTHER,
                                false,
                                3,
                                Instant.now(),
                                Instant.now()));

        service.restore(CREDENTIAL_ID, OWNER_ID);

        assertFalse(credential.isDeleted());
        assertNull(credential.getDeletedAt());
        verify(auditService)
                .record(
                        eq(AuditAction.RESTORE),
                        eq("CREDENTIAL"),
                        eq(CREDENTIAL_ID),
                        eq(OWNER_ID),
                        anyString());
    }

    @Test
    void should_denyRestore_when_theCallerIsNotTheOwner() {
        Credential credential = ownedCredential("cipher", true);
        when(credentialRepository.findById(CREDENTIAL_ID)).thenReturn(Optional.of(credential));

        assertThrows(
                AccessDeniedException.class, () -> service.restore(CREDENTIAL_ID, OTHER_USER_ID));
    }

    // ---- permanentDelete: only ever operates on a trashed credential ----

    @Test
    void should_rejectPermanentDelete_when_theCredentialIsStillActive() {
        Credential credential = ownedCredential("cipher", false);
        when(credentialRepository.findById(CREDENTIAL_ID)).thenReturn(Optional.of(credential));

        assertThrows(
                CredentialNotFoundException.class,
                () -> service.permanentDelete(CREDENTIAL_ID, OWNER_ID));
        verify(credentialRepository, never()).delete(any(Credential.class));
    }

    @Test
    void should_hardDeleteHistoryAndSharesTogether_when_permanentlyDeletingATrashedCredential() {
        Credential credential = ownedCredential("cipher", true);
        when(credentialRepository.findById(CREDENTIAL_ID)).thenReturn(Optional.of(credential));
        when(passwordHistoryRepository.deleteByCredentialId(CREDENTIAL_ID)).thenReturn(2L);
        when(credentialShareRepository.deleteByCredentialId(CREDENTIAL_ID)).thenReturn(1L);

        service.permanentDelete(CREDENTIAL_ID, OWNER_ID);

        verify(credentialRepository).delete(credential);
        verify(vaultAnomalyDetector).recordPermanentDelete(OWNER_ID);
        verify(auditService)
                .record(
                        eq(AuditAction.PERMANENT_DELETE),
                        eq("CREDENTIAL"),
                        eq(CREDENTIAL_ID),
                        eq(OWNER_ID),
                        anyString());
    }

    // ---- getHealth: reuse/staleness/score aggregation ----

    @Test
    void should_returnAPerfectScore_when_theVaultIsEmpty() {
        when(credentialRepository.findByUserIdAndDeletedFalse(OWNER_ID)).thenReturn(List.of());

        var health = service.getHealth(OWNER_ID);

        assertEquals(0, health.totalCredentials());
        assertEquals(100, health.healthScore());
    }

    @Test
    void should_matchTheDocumentedFormula_when_twoOfThreeCredentialsShareAPassword() {
        Credential weak = credentialWith("cipher-weak", (short) 0, Instant.now());
        Credential strongA = credentialWith("cipher-strong-a", (short) 5, Instant.now());
        Credential strongB = credentialWith("cipher-strong-b", (short) 5, Instant.now());
        when(credentialRepository.findByUserIdAndDeletedFalse(OWNER_ID))
                .thenReturn(List.of(weak, strongA, strongB));
        when(passwordStrengthService.labelForScore(0)).thenReturn("Very Weak");
        when(passwordStrengthService.labelForScore(5)).thenReturn("Very Strong");
        when(aesEncryptionService.decrypt("cipher-weak")).thenReturn("WeakPass1");
        when(aesEncryptionService.decrypt("cipher-strong-a")).thenReturn("SharedStrongPass1!");
        when(aesEncryptionService.decrypt("cipher-strong-b")).thenReturn("SharedStrongPass1!");

        var health = service.getHealth(OWNER_ID);

        assertEquals(3, health.totalCredentials());
        assertEquals(1, health.veryWeakCount());
        assertEquals(2, health.veryStrongCount());
        assertEquals(2, health.reusedPasswordCount());
        assertEquals(0, health.staleCredentialCount());
        // (avg 3.333/5)*60 + (1 - 2/3)*25 + (1 - 0)*15 = 40.0 + 8.33 + 15 = 63.33 -> 63
        assertEquals(63, health.healthScore());
    }

    @Test
    void should_countAsStale_when_thePasswordHasNotChangedInOverNinetyDays() {
        Credential stale =
                credentialWith("cipher", (short) 5, Instant.now().minus(100, ChronoUnit.DAYS));
        when(credentialRepository.findByUserIdAndDeletedFalse(OWNER_ID)).thenReturn(List.of(stale));
        when(passwordStrengthService.labelForScore(5)).thenReturn("Very Strong");
        when(aesEncryptionService.decrypt("cipher")).thenReturn("SoloPass1!");

        var health = service.getHealth(OWNER_ID);

        assertEquals(1, health.staleCredentialCount());
        // (5/5)*60 + (1-0)*25 + (1-1)*15 = 60 + 25 + 0 = 85
        assertEquals(85, health.healthScore());
    }

    private Credential credentialWith(
            String encryptedPassword, short strengthScore, Instant passwordChangedAt) {
        return Credential.builder()
                .id(CREDENTIAL_ID)
                .user(User.builder().id(OWNER_ID).build())
                .title("Site")
                .encryptedPassword(encryptedPassword)
                .category(Category.OTHER)
                .strengthScore(strengthScore)
                .passwordChangedAt(passwordChangedAt)
                .deleted(false)
                .build();
    }

    // ---- recomputeStrengthForUser (S4.6 bulk async path) ----

    @Test
    void should_recomputeStrengthForEveryActiveCredential_when_bulkRecomputeRuns() {
        Credential c1 = credentialWith("cipher-1", (short) 1, Instant.now());
        Credential c2 = credentialWith("cipher-2", (short) 1, Instant.now());
        when(credentialRepository.findByUserIdAndDeletedFalse(OWNER_ID))
                .thenReturn(List.of(c1, c2));
        when(aesEncryptionService.decrypt("cipher-1")).thenReturn("PassOne1!");
        when(aesEncryptionService.decrypt("cipher-2")).thenReturn("PassTwo2@");
        when(passwordStrengthService.analyze("PassOne1!"))
                .thenReturn(new PasswordStrengthResponse(4, "Strong", 40.0, List.of()));
        when(passwordStrengthService.analyze("PassTwo2@"))
                .thenReturn(new PasswordStrengthResponse(5, "Very Strong", 60.0, List.of()));

        service.recomputeStrengthForUser(OWNER_ID);

        assertEquals((short) 4, c1.getStrengthScore());
        assertEquals((short) 5, c2.getStrengthScore());
        verify(credentialRepository).saveAll(List.of(c1, c2));
    }
}
