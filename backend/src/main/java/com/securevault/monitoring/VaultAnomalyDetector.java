package com.securevault.monitoring;

public interface VaultAnomalyDetector {

    /** Call on every credential read (owner or shared) — rule 3, P5.5 step 3. */
    void recordAccess(Long userId);

    /** Call on every successful permanent delete — rule 4, P5.5 step 3. */
    void recordPermanentDelete(Long userId);
}
