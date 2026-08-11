package com.securevault.security;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MfaBackupCodeRepository extends JpaRepository<MfaBackupCode, Long> {

    List<MfaBackupCode> findByUserIdAndUsedFalse(Long userId);

    @Modifying
    @Query("DELETE FROM MfaBackupCode c WHERE c.user.id = :userId")
    int deleteByUserId(@Param("userId") Long userId);
}
