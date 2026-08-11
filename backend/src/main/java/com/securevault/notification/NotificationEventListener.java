package com.securevault.notification;

import com.securevault.monitoring.AlertType;
import com.securevault.monitoring.SecurityAlertRaisedEvent;
import com.securevault.sharing.CredentialSharedEvent;
import com.securevault.sharing.ShareRevokedEvent;
import com.securevault.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * P5.6 step 3: every handler here is @TransactionalEventListener(phase = AFTER_COMMIT) — a
 * notification (and its email) only ever fires once the business transaction that triggered it has
 * actually committed. Without AFTER_COMMIT (the default phase is BEFORE_COMMIT, easy to get wrong),
 * a share that later rolled back for an unrelated reason would still have emailed the recipient
 * about access they never actually got — see ADR for the full reasoning. One listener class covers
 * all five P5.6 triggers: new-device login and security alert both arrive as
 * SecurityAlertRaisedEvent (S5.5 already raises a SecurityAlert for both), and
 * credential-shared/share-revoked/password-expiry each have their own event.
 */
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationService notificationService;
    private final EmailService emailService;
    private final UserRepository userRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSecurityAlert(SecurityAlertRaisedEvent event) {
        NotificationType type =
                event.type() == AlertType.NEW_DEVICE
                        ? NotificationType.NEW_DEVICE_LOGIN
                        : NotificationType.SECURITY_ALERT;
        String title =
                type == NotificationType.NEW_DEVICE_LOGIN ? "New device login" : "Security alert";
        notifyAndEmail(event.userId(), type, title, event.message());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCredentialShared(CredentialSharedEvent event) {
        String message =
                event.ownerEmail()
                        + " shared \""
                        + event.credentialTitle()
                        + "\" with you ("
                        + event.permission()
                        + " access)";
        notifyAndEmail(
                event.sharedWithUserId(),
                NotificationType.CREDENTIAL_SHARED,
                "Credential shared with you",
                message);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onShareRevoked(ShareRevokedEvent event) {
        notifyAndEmail(
                event.sharedWithUserId(),
                NotificationType.SHARE_REVOKED,
                "Share access revoked",
                "Your access to \"" + event.credentialTitle() + "\" has been revoked");
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPasswordExpiry(PasswordExpiryWarningEvent event) {
        notifyAndEmail(
                event.userId(),
                NotificationType.PASSWORD_EXPIRY,
                "Password expiry warning",
                event.staleCredentialCount()
                        + " of your saved passwords are older than 90 days and should be updated");
    }

    private void notifyAndEmail(Long userId, NotificationType type, String title, String message) {
        notificationService.create(userId, type, title, message);
        userRepository
                .findById(userId)
                .ifPresent(u -> emailService.send(u.getEmail(), title, message));
    }
}
