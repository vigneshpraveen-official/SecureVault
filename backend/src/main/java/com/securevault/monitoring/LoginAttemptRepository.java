package com.securevault.monitoring;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, Long> {

    List<LoginAttempt> findByEmailOrderByAttemptedAtDesc(String email);

    List<LoginAttempt> findAllByOrderByAttemptedAtDesc();

    long countByEmailAndSuccessfulFalseAndAttemptedAtAfter(String email, Instant since);

    @Query(
            "SELECT MAX(a.attemptedAt) FROM LoginAttempt a WHERE a.email = :email AND a.successful ="
                    + " false")
    Optional<Instant> findLatestFailureTime(@Param("email") String email);

    @Query(
            "SELECT MAX(a.attemptedAt) FROM LoginAttempt a WHERE a.email = :email AND a.successful ="
                    + " true")
    Optional<Instant> findLatestSuccessTime(@Param("email") String email);

    long countBySuccessfulFalseAndAttemptedAtAfter(Instant since);
}
