package com.securevault.monitoring;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SecurityAlertRepository extends JpaRepository<SecurityAlert, Long> {

    List<SecurityAlert> findByUserIdAndResolvedFalseOrderByCreatedAtDesc(Long userId);

    List<SecurityAlert> findAllByOrderByCreatedAtDesc();

    @Query(
            "SELECT a.severity, COUNT(a) FROM SecurityAlert a WHERE a.resolved = false GROUP BY a.severity")
    List<Object[]> countUnresolvedBySeverity();
}
