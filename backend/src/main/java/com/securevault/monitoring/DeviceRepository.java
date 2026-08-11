package com.securevault.monitoring;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceRepository extends JpaRepository<Device, Long> {

    Optional<Device> findByUserIdAndDeviceFingerprint(Long userId, String deviceFingerprint);

    List<Device> findByUserIdOrderByLastSeenAtDesc(Long userId);

    long countByUserId(Long userId);
}
