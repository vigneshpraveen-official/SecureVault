package com.securevault.vault;

import com.securevault.security.crypto.AesEncryptionService;
import com.securevault.user.User;
import com.securevault.user.UserRepository;
import com.securevault.vault.dto.CredentialCreateRequest;
import com.securevault.vault.dto.CredentialDetailResponse;
import com.securevault.vault.dto.CredentialResponse;
import com.securevault.vault.dto.CredentialUpdateRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CredentialServiceImpl implements CredentialService {

    private final CredentialRepository credentialRepository;
    private final UserRepository userRepository;
    private final AesEncryptionService aesEncryptionService;

    @Override
    @Transactional
    public CredentialResponse create(Long userId, CredentialCreateRequest request) {
        User owner = userRepository.getReferenceById(userId);

        Credential credential =
                Credential.builder()
                        .user(owner)
                        .title(request.title())
                        .username(request.username())
                        .encryptedPassword(aesEncryptionService.encrypt(request.password()))
                        .websiteUrl(request.websiteUrl())
                        .notes(request.notes())
                        .category(request.category() != null ? request.category() : Category.OTHER)
                        .build();

        return CredentialResponse.from(credentialRepository.save(credential));
    }

    @Override
    public CredentialDetailResponse getByIdForUser(Long id, Long userId) {
        Credential credential = loadOwned(id, userId);
        String decrypted = aesEncryptionService.decrypt(credential.getEncryptedPassword());
        return CredentialDetailResponse.from(credential, decrypted);
    }

    @Override
    public List<CredentialResponse> listForUser(Long userId, Category category) {
        List<Credential> credentials =
                category == null
                        ? credentialRepository.findByUserId(userId)
                        : credentialRepository.findByUserIdAndCategory(userId, category);
        return credentials.stream().map(CredentialResponse::from).toList();
    }

    @Override
    public List<CredentialResponse> search(Long userId, String term) {
        return credentialRepository.search(userId, term).stream()
                .map(CredentialResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public CredentialResponse update(Long id, Long userId, CredentialUpdateRequest request) {
        Credential credential = loadOwned(id, userId);

        if (request.title() != null) {
            credential.setTitle(request.title());
        }
        if (request.username() != null) {
            credential.setUsername(request.username());
        }
        if (request.websiteUrl() != null) {
            credential.setWebsiteUrl(request.websiteUrl());
        }
        if (request.notes() != null) {
            credential.setNotes(request.notes());
        }
        if (request.category() != null) {
            credential.setCategory(request.category());
        }
        if (request.password() != null) {
            // Ciphertext can never be compared directly — GCM uses a fresh random IV every
            // encryption (D-05), so the same plaintext never produces the same stored value
            // twice. The only reliable way to detect "actually changed" is to decrypt the
            // current value and compare plaintext to plaintext.
            String currentPlaintext =
                    aesEncryptionService.decrypt(credential.getEncryptedPassword());
            if (!currentPlaintext.equals(request.password())) {
                credential.setEncryptedPassword(aesEncryptionService.encrypt(request.password()));
            }
        }

        return CredentialResponse.from(credentialRepository.save(credential));
    }

    @Override
    @Transactional
    public void delete(Long id, Long userId) {
        Credential credential = loadOwned(id, userId);
        // Hard delete for now — soft delete (deleted/deletedAt flags, M-37..M-39) replaces this
        // in S4.3. Intentional simplification for this session, not an oversight.
        credentialRepository.delete(credential);
    }

    private Credential loadOwned(Long id, Long userId) {
        Credential credential =
                credentialRepository
                        .findById(id)
                        .orElseThrow(() -> new CredentialNotFoundException(id));
        if (!credential.getUser().getId().equals(userId)) {
            throw new CredentialAccessDeniedException();
        }
        return credential;
    }
}
