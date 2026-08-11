package com.securevault.monitoring;

import com.securevault.monitoring.dto.LoginAttemptResponse;
import java.util.List;

public interface LoginAttemptService {

    /** Resets the user's failure counter/lock on a successful login. */
    void recordSuccess(String email, Long userId, String ip, String userAgent);

    /**
     * Records a failed attempt and locks the account after 5 consecutive failures within 15 minutes
     * (P5.5 step 2). No-op locking if the email doesn't resolve to a user — the attempt is still
     * logged either way.
     */
    void recordFailure(String email, String ip, String userAgent, String failureReason);

    List<LoginAttemptResponse> listForUser(String email);

    List<LoginAttemptResponse> listAll();
}
