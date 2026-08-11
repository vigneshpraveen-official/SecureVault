package com.securevault.notification;

public interface PasswordExpiryCheckService {

    /**
     * Scans for credentials older than 90 days and publishes one warning event per affected user.
     */
    void checkAndNotify();
}
