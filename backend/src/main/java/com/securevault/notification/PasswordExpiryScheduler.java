package com.securevault.notification;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Default: once daily at 06:00 server time. Overridable via app.notification.password-expiry-cron
 * for local verification (e.g. every 10s) without touching the committed schedule.
 */
@Component
@RequiredArgsConstructor
public class PasswordExpiryScheduler {

    private final PasswordExpiryCheckService passwordExpiryCheckService;

    @Scheduled(cron = "${app.notification.password-expiry-cron:0 0 6 * * *}")
    public void run() {
        passwordExpiryCheckService.checkAndNotify();
    }
}
