package com.securevault.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Deliberate, bounded pool sizing (P4.6/M-40) — not defaults:
 *
 * <ul>
 *   <li>corePoolSize=4 — SecureVault's async work (email, activity logging, strength recompute) is
 *       bursty and low-volume, not a sustained high-throughput pipeline; 4 threads is enough to
 *       keep several tasks moving without holding idle threads most of the time.
 *   <li>maxPoolSize=8 — a ceiling under load, not "as many as needed"; twice core lets bursts
 *       through without letting the pool grow unbounded.
 *   <li>queueCapacity=50 — bounded on purpose. An unbounded queue (the {@code
 *       ThreadPoolTaskExecutor} default) turns backpressure into an OOM risk instead of a visible
 *       failure — 50 queued tasks is a generous buffer for this app's scale without hiding a real
 *       overload.
 *   <li>{@code CallerRunsPolicy} — when both the pool and the queue are full, the task runs on the
 *       calling thread instead of being silently dropped or throwing a rejection exception. For
 *       SecureVault's async work (a welcome email, an activity log line, a strength recompute)
 *       that's the right tradeoff: a brief slowdown on the caller is better than a lost
 *       notification or a failed background task.
 * </ul>
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("sv-async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // Carries the request's correlation id onto the worker thread (P4.7) — see
        // MdcTaskDecorator's javadoc for why this isn't automatic.
        executor.setTaskDecorator(new MdcTaskDecorator());
        executor.initialize();
        return executor;
    }
}
