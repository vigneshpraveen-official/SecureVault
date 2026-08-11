package com.securevault.notification;

/**
 * Published by PasswordExpiryCheckService's daily sweep (P5.6 trigger: "password expiry warning").
 */
public record PasswordExpiryWarningEvent(Long userId, int staleCredentialCount) {}
