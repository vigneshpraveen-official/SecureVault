package com.securevault.common.audit;

import org.springframework.data.jpa.repository.JpaRepository;

/** Insert-only from the application's perspective — no update/delete methods are ever added. */
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {}
