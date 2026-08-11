package com.securevault.sharing;

import com.securevault.common.audit.AuditAction;
import com.securevault.common.audit.AuditService;
import com.securevault.common.exception.AccessDeniedException;
import com.securevault.common.exception.CredentialNotFoundException;
import com.securevault.common.exception.SelfShareNotAllowedException;
import com.securevault.common.exception.ShareAlreadyExistsException;
import com.securevault.common.exception.UserNotFoundException;
import com.securevault.common.util.LogMasking;
import com.securevault.sharing.dto.ShareCreateRequest;
import com.securevault.sharing.dto.SharePermissionUpdateRequest;
import com.securevault.sharing.dto.ShareResponse;
import com.securevault.user.User;
import com.securevault.user.UserRepository;
import com.securevault.vault.Credential;
import com.securevault.vault.CredentialRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CredentialShareServiceImpl implements CredentialShareService {

    private final CredentialShareRepository credentialShareRepository;
    private final CredentialRepository credentialRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public ShareResponse create(Long ownerId, ShareCreateRequest request) {
        Credential credential =
                credentialRepository
                        .findByIdAndDeletedFalse(request.credentialId())
                        .orElseThrow(() -> new CredentialNotFoundException(request.credentialId()));
        // Only the owner can share (M-45) — a shared EDIT user attempting this hits the same 403
        // an unrelated user would, since neither is the owner.
        if (!credential.getUser().getId().equals(ownerId)) {
            throw new AccessDeniedException("Only the owner can share this credential");
        }

        User sharedWithUser =
                userRepository
                        .findByEmail(request.sharedWithEmail())
                        .orElseThrow(() -> new UserNotFoundException(request.sharedWithEmail()));
        if (sharedWithUser.getId().equals(ownerId)) {
            throw new SelfShareNotAllowedException();
        }
        credentialShareRepository
                .findByCredentialIdAndSharedWithUserIdAndActiveTrue(
                        credential.getId(), sharedWithUser.getId())
                .ifPresent(
                        existing -> {
                            throw new ShareAlreadyExistsException();
                        });

        CredentialShare share =
                CredentialShare.builder()
                        .credential(credential)
                        .owner(credential.getUser())
                        .sharedWithUser(sharedWithUser)
                        .permission(request.permission())
                        .expiresAt(request.expiresAt())
                        .active(true)
                        .build();
        CredentialShare saved = credentialShareRepository.save(share);

        auditService.record(
                AuditAction.SHARE,
                "CREDENTIAL_SHARE",
                saved.getId(),
                ownerId,
                "credentialId="
                        + credential.getId()
                        + ", sharedWith="
                        + sharedWithUser.getEmail()
                        + ", permission="
                        + request.permission());
        log.info(
                "Credential shared: credentialId={}, ownerId={}, sharedWith={}, permission={}",
                credential.getId(),
                ownerId,
                LogMasking.maskEmail(sharedWithUser.getEmail()),
                request.permission());
        // AFTER_COMMIT (P5.6 step 3, NotificationEventListener) — fires only once this whole
        // transaction actually commits, not on every call to create().
        eventPublisher.publishEvent(
                new CredentialSharedEvent(
                        sharedWithUser.getId(),
                        credential.getUser().getEmail(),
                        credential.getTitle(),
                        request.permission()));

        return toResponse(saved);
    }

    @Override
    public List<ShareResponse> received(Long userId) {
        return credentialShareRepository.findReceivedByUserId(userId);
    }

    @Override
    public List<ShareResponse> sent(Long ownerId) {
        return credentialShareRepository.findSentByOwnerId(ownerId);
    }

    @Override
    @Transactional
    public ShareResponse updatePermission(
            Long shareId, Long ownerId, SharePermissionUpdateRequest request) {
        CredentialShare share = loadOwnedShare(shareId, ownerId);
        share.setPermission(request.permission());
        CredentialShare saved = credentialShareRepository.save(share);
        auditService.record(
                AuditAction.SHARE,
                "CREDENTIAL_SHARE",
                saved.getId(),
                ownerId,
                "permission changed to " + request.permission());
        log.info("Share permission updated: shareId={}, ownerId={}", shareId, ownerId);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public void revoke(Long shareId, Long ownerId) {
        // Soft revoke (active=false), not a hard delete — same reasoning as credential soft
        // delete: the row (and its audit trail) stays for accountability. Idempotent: revoking an
        // already-revoked share just re-sets active=false rather than erroring (ADR-023).
        CredentialShare share = loadOwnedShare(shareId, ownerId);
        share.setActive(false);
        credentialShareRepository.save(share);
        auditService.record(
                AuditAction.REVOKE, "CREDENTIAL_SHARE", share.getId(), ownerId, "revoked");
        log.info("Share revoked: shareId={}, ownerId={}", shareId, ownerId);
        eventPublisher.publishEvent(
                new ShareRevokedEvent(
                        share.getSharedWithUser().getId(), share.getCredential().getTitle()));
    }

    // Not-found and not-owned collapse into the same 403 ACCESS_DENIED (ADR-023) — master §9's
    // ErrorCode enum has no SHARE_NOT_FOUND code, and this avoids disclosing whether a shareId
    // belonging to someone else even exists, same spirit as login's InvalidCredentialsException.
    private CredentialShare loadOwnedShare(Long shareId, Long ownerId) {
        CredentialShare share =
                credentialShareRepository.findById(shareId).orElseThrow(AccessDeniedException::new);
        if (!share.getOwner().getId().equals(ownerId)) {
            throw new AccessDeniedException();
        }
        return share;
    }

    // Called only right after building/loading the entity within the same transaction, so
    // credential/owner/sharedWithUser are either the exact objects already in hand (create) or a
    // single non-list lazy load (update/revoke) — never the per-row N+1 pattern P4.4 fixed.
    private ShareResponse toResponse(CredentialShare share) {
        boolean expired =
                share.getExpiresAt() != null && share.getExpiresAt().isBefore(Instant.now());
        return new ShareResponse(
                share.getId(),
                share.getCredential().getId(),
                share.getCredential().getTitle(),
                share.getOwner().getId(),
                share.getOwner().getEmail(),
                share.getSharedWithUser().getId(),
                share.getSharedWithUser().getEmail(),
                share.getPermission(),
                share.getSharedAt(),
                share.getExpiresAt(),
                share.isActive(),
                expired);
    }
}
