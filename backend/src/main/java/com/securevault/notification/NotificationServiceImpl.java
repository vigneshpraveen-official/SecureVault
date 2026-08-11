package com.securevault.notification;

import com.securevault.common.exception.AccessDeniedException;
import com.securevault.notification.dto.NotificationResponse;
import com.securevault.user.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    // REQUIRES_NEW, not the REQUIRED default (found live, ADR-025): every caller of create() is
    // NotificationEventListener, invoked from a @TransactionalEventListener(phase = AFTER_COMMIT)
    // callback. At that point in Spring's commit sequence, the just-committed transaction's
    // resources can still be thread-bound (afterCommit() runs BEFORE cleanupAfterCompletion()),
    // so a plain @Transactional(REQUIRED) call here silently "participates" in that
    // already-finished, about-to-be-torn-down transaction instead of starting a fresh one — the
    // save() below returned normally with a null id and no INSERT ever reached Postgres, no
    // exception anywhere. REQUIRES_NEW forces a genuinely new transaction/persistence context
    // regardless of whatever zombie state the thread is carrying.
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void create(Long userId, NotificationType type, String title, String message) {
        notificationRepository.save(
                Notification.builder()
                        .user(userRepository.getReferenceById(userId))
                        .type(type)
                        .title(title)
                        .message(message)
                        .read(false)
                        .build());
    }

    @Override
    public List<NotificationResponse> list(Long userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(NotificationServiceImpl::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public void markRead(Long notificationId, Long userId) {
        // Not-found/not-owned collapse into one 403 (ADR-023 precedent).
        Notification notification =
                notificationRepository
                        .findById(notificationId)
                        .orElseThrow(AccessDeniedException::new);
        if (!notification.getUser().getId().equals(userId)) {
            throw new AccessDeniedException();
        }
        notification.setRead(true);
        notificationRepository.save(notification);
    }

    @Override
    @Transactional
    public void markAllRead(Long userId) {
        notificationRepository.markAllRead(userId);
    }

    private static NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(
                n.getId(), n.getType(), n.getTitle(), n.getMessage(), n.isRead(), n.getCreatedAt());
    }
}
