package com.securevault.sharing;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AccessEvaluatorImpl implements AccessEvaluator {

    private final CredentialShareRepository credentialShareRepository;

    @Override
    public AccessLevel evaluate(Long credentialId, Long ownerId, Long userId) {
        if (ownerId.equals(userId)) {
            return AccessLevel.OWNER;
        }
        return credentialShareRepository
                .findByCredentialIdAndSharedWithUserIdAndActiveTrue(credentialId, userId)
                .filter(
                        share ->
                                share.getExpiresAt() == null
                                        || share.getExpiresAt().isAfter(Instant.now()))
                .map(
                        share ->
                                share.getPermission() == SharePermission.EDIT
                                        ? AccessLevel.EDIT
                                        : AccessLevel.READ)
                .orElse(AccessLevel.NONE);
    }
}
