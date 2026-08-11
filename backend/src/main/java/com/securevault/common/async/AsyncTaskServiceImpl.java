package com.securevault.common.async;

import com.securevault.common.util.LogMasking;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AsyncTaskServiceImpl implements AsyncTaskService {

    @Async("taskExecutor")
    @Override
    public void sendNotificationEmail(String toEmail, String subject, String body) {
        // Simulated: logs only, never actually sends (real SMTP wiring is S5.6). The point of
        // this session is proving the request thread doesn't block on it.
        log.info(
                "[{}] Simulated email -> {} : {}",
                Thread.currentThread().getName(),
                LogMasking.maskEmail(toEmail),
                subject);
    }

    @Async("taskExecutor")
    @Override
    public void logActivity(String message) {
        log.info("[{}] Activity: {}", Thread.currentThread().getName(), message);
    }
}
