package com.securevault.common.audit;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Insert-only from the application's perspective — no update/delete methods are ever added. */
public interface AuditLogRepository
        extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {

    List<AuditLog> findTop20ByPerformedByOrderByTimestampDesc(Long performedBy);
}
