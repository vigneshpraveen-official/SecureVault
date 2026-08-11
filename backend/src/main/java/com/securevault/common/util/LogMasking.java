package com.securevault.common.util;

/** Never log a full email address in WARN/ERROR if avoidable — mask it (P4.7/M-47). */
public final class LogMasking {

    private LogMasking() {}

    public static String maskEmail(String email) {
        if (email == null) {
            return "***";
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
