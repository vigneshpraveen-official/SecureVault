package com.securevault.sharing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The single authorisation decision point every vault operation goes through (P5.1/M-44) — every
 * rule from the S5.1 access matrix is a branch in this class, so it gets its own focused test
 * rather than being exercised only indirectly through CredentialServiceImplTest/
 * CredentialShareServiceImplTest.
 */
@ExtendWith(MockitoExtension.class)
class AccessEvaluatorImplTest {

    private static final Long CREDENTIAL_ID = 100L;
    private static final Long OWNER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;

    @Mock private CredentialShareRepository credentialShareRepository;

    private AccessEvaluatorImpl accessEvaluator;

    @BeforeEach
    void setUp() {
        accessEvaluator = new AccessEvaluatorImpl(credentialShareRepository);
    }

    @Test
    void should_returnOwner_when_theCallerIsTheOwner_withoutConsultingShares() {
        AccessLevel level = accessEvaluator.evaluate(CREDENTIAL_ID, OWNER_ID, OWNER_ID);

        assertEquals(AccessLevel.OWNER, level);
    }

    @Test
    void should_returnNone_when_thereIsNoActiveShareForThisUser() {
        when(credentialShareRepository.findByCredentialIdAndSharedWithUserIdAndActiveTrue(
                        CREDENTIAL_ID, OTHER_USER_ID))
                .thenReturn(Optional.empty());

        AccessLevel level = accessEvaluator.evaluate(CREDENTIAL_ID, OWNER_ID, OTHER_USER_ID);

        assertEquals(AccessLevel.NONE, level);
    }

    @Test
    void should_returnRead_when_anActiveUnexpiredReadShareExists() {
        CredentialShare share =
                CredentialShare.builder().permission(SharePermission.READ).expiresAt(null).build();
        when(credentialShareRepository.findByCredentialIdAndSharedWithUserIdAndActiveTrue(
                        CREDENTIAL_ID, OTHER_USER_ID))
                .thenReturn(Optional.of(share));

        AccessLevel level = accessEvaluator.evaluate(CREDENTIAL_ID, OWNER_ID, OTHER_USER_ID);

        assertEquals(AccessLevel.READ, level);
    }

    @Test
    void should_returnEdit_when_anActiveUnexpiredEditShareExists() {
        CredentialShare share =
                CredentialShare.builder().permission(SharePermission.EDIT).expiresAt(null).build();
        when(credentialShareRepository.findByCredentialIdAndSharedWithUserIdAndActiveTrue(
                        CREDENTIAL_ID, OTHER_USER_ID))
                .thenReturn(Optional.of(share));

        AccessLevel level = accessEvaluator.evaluate(CREDENTIAL_ID, OWNER_ID, OTHER_USER_ID);

        assertEquals(AccessLevel.EDIT, level);
    }

    @Test
    void should_returnNone_when_theShareHasExpired() {
        // An expired share behaves exactly like no share at all (M-45) — this repository query
        // only returns rows where active=true, so an expired-but-still-active-flagged row is what
        // this evaluator itself must catch.
        CredentialShare share =
                CredentialShare.builder()
                        .permission(SharePermission.EDIT)
                        .expiresAt(Instant.now().minus(1, ChronoUnit.HOURS))
                        .build();
        when(credentialShareRepository.findByCredentialIdAndSharedWithUserIdAndActiveTrue(
                        CREDENTIAL_ID, OTHER_USER_ID))
                .thenReturn(Optional.of(share));

        AccessLevel level = accessEvaluator.evaluate(CREDENTIAL_ID, OWNER_ID, OTHER_USER_ID);

        assertEquals(AccessLevel.NONE, level);
    }

    @Test
    void should_returnRead_when_theShareExpiresInTheFuture() {
        CredentialShare share =
                CredentialShare.builder()
                        .permission(SharePermission.READ)
                        .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                        .build();
        when(credentialShareRepository.findByCredentialIdAndSharedWithUserIdAndActiveTrue(
                        CREDENTIAL_ID, OTHER_USER_ID))
                .thenReturn(Optional.of(share));

        AccessLevel level = accessEvaluator.evaluate(CREDENTIAL_ID, OWNER_ID, OTHER_USER_ID);

        assertEquals(AccessLevel.READ, level);
    }
}
