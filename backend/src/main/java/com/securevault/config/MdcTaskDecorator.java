package com.securevault.config;

import java.util.Map;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;
import org.springframework.lang.NonNull;

/**
 * Without this, an @Async task's log lines show no correlation id — MDC is ThreadLocal, and @Async
 * hands the task to a different thread from AsyncConfig's pool, which doesn't inherit the calling
 * thread's ThreadLocal state automatically (found while capturing S4.7 evidence: the async
 * activity-log line for a login showed "[-]" instead of the request's correlation id). Copies the
 * MDC context map at submission time and restores it on the worker thread for the duration of the
 * task, then clears it — the worker thread returns to the pool afterward and must not leak this
 * task's correlation id into whichever task it picks up next.
 */
public class MdcTaskDecorator implements TaskDecorator {

    @Override
    @NonNull
    public Runnable decorate(@NonNull Runnable runnable) {
        Map<String, String> contextMap = MDC.getCopyOfContextMap();
        return () -> {
            try {
                if (contextMap != null) {
                    MDC.setContextMap(contextMap);
                }
                runnable.run();
            } finally {
                MDC.clear();
            }
        };
    }
}
