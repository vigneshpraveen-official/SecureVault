package com.securevault.vault;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CredentialRepository extends JpaRepository<Credential, Long> {

    List<Credential> findByUserId(Long userId);

    List<Credential> findByUserIdAndCategory(Long userId, Category category);

    // @Query (JPQL) over a Spring Data derived name: a derived name for "one AND (three OR'd,
    // case-insensitive, partial-match fields)" would need userId repeated in every OR clause
    // (Spring Data's method-name grammar has no grouping), which is exactly the kind of long,
    // easy-to-get-wrong method name this avoids. JPQL makes the boolean structure explicit.
    @Query(
            "SELECT c FROM Credential c "
                    + "WHERE c.user.id = :userId "
                    + "AND (LOWER(c.title) LIKE LOWER(CONCAT('%', :term, '%')) "
                    + "OR LOWER(c.username) LIKE LOWER(CONCAT('%', :term, '%')) "
                    + "OR LOWER(c.websiteUrl) LIKE LOWER(CONCAT('%', :term, '%')))")
    List<Credential> search(@Param("userId") Long userId, @Param("term") String term);
}
