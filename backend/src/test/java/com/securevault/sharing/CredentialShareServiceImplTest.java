package com.securevault.sharing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.securevault.common.audit.AuditService;
import com.securevault.common.exception.AccessDeniedException;
import com.securevault.common.exception.CredentialNotFoundException;
import com.securevault.common.exception.SelfShareNotAllowedException;
import com.securevault.common.exception.ShareAlreadyExistsException;
import com.securevault.common.exception.UserNotFoundException;
import com.securevault.sharing.dto.ShareCreateRequest;
import com.securevault.sharing.dto.SharePermissionUpdateRequest;
import com.securevault.sharing.dto.ShareResponse;
import com.securevault.user.User;
import com.securevault.user.UserRepository;
import com.securevault.vault.Category;
import com.securevault.vault.Credential;
import com.securevault.vault.CredentialRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class CredentialShareServiceImplTest {

    private static final Long CREDENTIAL_ID = 100L;
    private static final Long OWNER_ID = 1L;
    private static final Long RECIPIENT_ID = 2L;
    private static final Long SHARE_ID = 500L;

    @Mock private CredentialShareRepository credentialShareRepository;
    @Mock private CredentialRepository credentialRepository;
    @Mock private UserRepository userRepository;
    @Mock private AuditService auditService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private CredentialShareServiceImpl service;

    @BeforeEach
    void setUp() {
        service =
                new CredentialShareServiceImpl(
                        credentialShareRepository,
                        credentialRepository,
                        userRepository,
                        auditService,
                        eventPublisher);
    }

    private Credential credentialOwnedBy(Long ownerId) {
        return Credential.builder()
                .id(CREDENTIAL_ID)
                .user(User.builder().id(ownerId).build())
                .title("GitHub")
                .encryptedPassword("cipher")
                .category(Category.OTHER)
                .passwordChangedAt(Instant.now())
                .build();
    }

    // ---- create: every S5.1 business rule ----

    @Test
    void should_rejectShare_when_theCredentialDoesNotExist() {
        ShareCreateRequest request =
                new ShareCreateRequest(
                        CREDENTIAL_ID, "bob@example.com", SharePermission.READ, null);
        when(credentialRepository.findByIdAndDeletedFalse(CREDENTIAL_ID))
                .thenReturn(Optional.empty());

        assertThrows(CredentialNotFoundException.class, () -> service.create(OWNER_ID, request));
        verify(credentialShareRepository, never()).save(any());
    }

    @Test
    void should_rejectShare_when_theCallerIsNotTheOwner() {
        // Only the owner can share (M-45) — a non-owner (including an EDIT-share holder) gets the
        // same 403 an unrelated stranger would.
        ShareCreateRequest request =
                new ShareCreateRequest(
                        CREDENTIAL_ID, "bob@example.com", SharePermission.READ, null);
        when(credentialRepository.findByIdAndDeletedFalse(CREDENTIAL_ID))
                .thenReturn(Optional.of(credentialOwnedBy(OWNER_ID)));

        assertThrows(AccessDeniedException.class, () -> service.create(RECIPIENT_ID, request));
        verify(userRepository, never()).findByEmail(anyString());
    }

    @Test
    void should_rejectShare_when_theRecipientEmailDoesNotResolveToAUser() {
        ShareCreateRequest request =
                new ShareCreateRequest(
                        CREDENTIAL_ID, "ghost@example.com", SharePermission.READ, null);
        when(credentialRepository.findByIdAndDeletedFalse(CREDENTIAL_ID))
                .thenReturn(Optional.of(credentialOwnedBy(OWNER_ID)));
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> service.create(OWNER_ID, request));
    }

    @Test
    void should_rejectShare_when_theOwnerTriesToShareWithThemselves() {
        ShareCreateRequest request =
                new ShareCreateRequest(
                        CREDENTIAL_ID, "owner@example.com", SharePermission.READ, null);
        when(credentialRepository.findByIdAndDeletedFalse(CREDENTIAL_ID))
                .thenReturn(Optional.of(credentialOwnedBy(OWNER_ID)));
        when(userRepository.findByEmail("owner@example.com"))
                .thenReturn(
                        Optional.of(
                                User.builder().id(OWNER_ID).email("owner@example.com").build()));

        assertThrows(SelfShareNotAllowedException.class, () -> service.create(OWNER_ID, request));
        verify(credentialShareRepository, never()).save(any());
    }

    @Test
    void should_rejectShare_when_anActiveShareAlreadyExistsForThatUser() {
        ShareCreateRequest request =
                new ShareCreateRequest(
                        CREDENTIAL_ID, "bob@example.com", SharePermission.READ, null);
        when(credentialRepository.findByIdAndDeletedFalse(CREDENTIAL_ID))
                .thenReturn(Optional.of(credentialOwnedBy(OWNER_ID)));
        when(userRepository.findByEmail("bob@example.com"))
                .thenReturn(
                        Optional.of(
                                User.builder().id(RECIPIENT_ID).email("bob@example.com").build()));
        when(credentialShareRepository.findByCredentialIdAndSharedWithUserIdAndActiveTrue(
                        CREDENTIAL_ID, RECIPIENT_ID))
                .thenReturn(Optional.of(CredentialShare.builder().id(SHARE_ID).build()));

        assertThrows(ShareAlreadyExistsException.class, () -> service.create(OWNER_ID, request));
        verify(credentialShareRepository, never()).save(any());
    }

    @Test
    void should_createTheShareAndPublishAnEvent_when_everyRuleIsSatisfied() {
        ShareCreateRequest request =
                new ShareCreateRequest(
                        CREDENTIAL_ID, "bob@example.com", SharePermission.READ, null);
        Credential credential = credentialOwnedBy(OWNER_ID);
        User owner = credential.getUser();
        owner.setEmail("owner@example.com");
        User recipient = User.builder().id(RECIPIENT_ID).email("bob@example.com").build();
        when(credentialRepository.findByIdAndDeletedFalse(CREDENTIAL_ID))
                .thenReturn(Optional.of(credential));
        when(userRepository.findByEmail("bob@example.com")).thenReturn(Optional.of(recipient));
        when(credentialShareRepository.findByCredentialIdAndSharedWithUserIdAndActiveTrue(
                        CREDENTIAL_ID, RECIPIENT_ID))
                .thenReturn(Optional.empty());
        when(credentialShareRepository.save(any(CredentialShare.class)))
                .thenAnswer(
                        invocation -> {
                            CredentialShare share = invocation.getArgument(0);
                            share.setId(SHARE_ID);
                            share.setSharedAt(Instant.now());
                            return share;
                        });

        ShareResponse response = service.create(OWNER_ID, request);

        assertEquals(SHARE_ID, response.id());
        assertEquals(SharePermission.READ, response.permission());
        assertFalse(response.expired());
        verify(auditService)
                .record(any(), eq("CREDENTIAL_SHARE"), eq(SHARE_ID), eq(OWNER_ID), anyString());
        verify(eventPublisher, times(1)).publishEvent(any(CredentialSharedEvent.class));
    }

    // ---- updatePermission / revoke: owner-only, not-found and not-owned both collapse to 403 ----

    @Test
    void should_updatePermission_when_theCallerIsTheOwner() {
        CredentialShare share =
                CredentialShare.builder()
                        .id(SHARE_ID)
                        .owner(User.builder().id(OWNER_ID).build())
                        .credential(credentialOwnedBy(OWNER_ID))
                        .sharedWithUser(
                                User.builder().id(RECIPIENT_ID).email("bob@example.com").build())
                        .permission(SharePermission.READ)
                        .sharedAt(Instant.now())
                        .active(true)
                        .build();
        when(credentialShareRepository.findById(SHARE_ID)).thenReturn(Optional.of(share));
        when(credentialShareRepository.save(any(CredentialShare.class)))
                .thenAnswer(i -> i.getArgument(0));

        ShareResponse response =
                service.updatePermission(
                        SHARE_ID, OWNER_ID, new SharePermissionUpdateRequest(SharePermission.EDIT));

        assertEquals(SharePermission.EDIT, response.permission());
    }

    @Test
    void should_denyPermissionUpdate_when_theCallerDoesNotOwnTheShare() {
        CredentialShare share =
                CredentialShare.builder()
                        .id(SHARE_ID)
                        .owner(User.builder().id(OWNER_ID).build())
                        .build();
        when(credentialShareRepository.findById(SHARE_ID)).thenReturn(Optional.of(share));

        assertThrows(
                AccessDeniedException.class,
                () ->
                        service.updatePermission(
                                SHARE_ID,
                                RECIPIENT_ID,
                                new SharePermissionUpdateRequest(SharePermission.EDIT)));
        verify(credentialShareRepository, never()).save(any());
    }

    @Test
    void should_denyPermissionUpdate_when_theShareDoesNotExist() {
        when(credentialShareRepository.findById(SHARE_ID)).thenReturn(Optional.empty());

        assertThrows(
                AccessDeniedException.class,
                () ->
                        service.updatePermission(
                                SHARE_ID,
                                OWNER_ID,
                                new SharePermissionUpdateRequest(SharePermission.EDIT)));
    }

    @Test
    void should_revokeAndPublishAnEvent_when_theCallerIsTheOwner() {
        CredentialShare share =
                CredentialShare.builder()
                        .id(SHARE_ID)
                        .owner(User.builder().id(OWNER_ID).build())
                        .credential(credentialOwnedBy(OWNER_ID))
                        .sharedWithUser(User.builder().id(RECIPIENT_ID).build())
                        .permission(SharePermission.READ)
                        .active(true)
                        .build();
        when(credentialShareRepository.findById(SHARE_ID)).thenReturn(Optional.of(share));
        when(credentialShareRepository.save(any(CredentialShare.class)))
                .thenAnswer(i -> i.getArgument(0));

        service.revoke(SHARE_ID, OWNER_ID);

        ArgumentCaptor<CredentialShare> captor = ArgumentCaptor.forClass(CredentialShare.class);
        verify(credentialShareRepository).save(captor.capture());
        assertFalse(captor.getValue().isActive());
        verify(eventPublisher).publishEvent(any(ShareRevokedEvent.class));
        verify(auditService)
                .record(any(), eq("CREDENTIAL_SHARE"), eq(SHARE_ID), eq(OWNER_ID), anyString());
    }

    @Test
    void should_denyRevoke_when_theCallerDoesNotOwnTheShare() {
        CredentialShare share =
                CredentialShare.builder()
                        .id(SHARE_ID)
                        .owner(User.builder().id(OWNER_ID).build())
                        .build();
        when(credentialShareRepository.findById(SHARE_ID)).thenReturn(Optional.of(share));

        assertThrows(AccessDeniedException.class, () -> service.revoke(SHARE_ID, RECIPIENT_ID));
        verify(credentialShareRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }
}
