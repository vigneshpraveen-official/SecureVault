package com.securevault.vault;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Soft-delete convention (P4.3/M-37..M-39): every "active" query is named *DeletedFalse explicitly,
 * rather than a global @Where/@SQLRestriction on the entity. A blanket @SQLRestriction would
 * silently apply to the trash/restore/permanent-delete queries too, which need to see deleted rows
 * by design — an explicit suffix on each method makes "does this query see deleted rows?"
 * answerable by reading the method name, not by knowing about a filter defined somewhere else on
 * the entity. findByUserId/findByCategory in Phase 1-3 style are gone; every list/search query
 * below is deleted-aware by construction. Also extends JpaSpecificationExecutor for S4.5's dynamic
 * filtering, which composes the same deleted=false predicate as one of several ANDed
 * Specifications.
 */
public interface CredentialRepository
        extends JpaRepository<Credential, Long>, JpaSpecificationExecutor<Credential> {

    List<Credential> findByUserIdAndDeletedFalse(Long userId);

    List<Credential> findByUserIdAndCategoryAndDeletedFalse(Long userId, Category category);

    List<Credential> findByUserIdAndDeletedTrue(Long userId);

    Optional<Credential> findByIdAndDeletedFalse(Long id);

    // @Query (JPQL) over a Spring Data derived name: a derived name for "one AND (three OR'd,
    // case-insensitive, partial-match fields) AND not deleted" would need userId/deleted repeated
    // in every OR clause (Spring Data's method-name grammar has no grouping), which is exactly
    // the kind of long, easy-to-get-wrong method name this avoids. JPQL makes the boolean
    // structure explicit.
    @Query(
            "SELECT c FROM Credential c "
                    + "WHERE c.user.id = :userId AND c.deleted = false "
                    + "AND (LOWER(c.title) LIKE LOWER(CONCAT('%', :term, '%')) "
                    + "OR LOWER(c.username) LIKE LOWER(CONCAT('%', :term, '%')) "
                    + "OR LOWER(c.websiteUrl) LIKE LOWER(CONCAT('%', :term, '%')))")
    List<Credential> search(@Param("userId") Long userId, @Param("term") String term);

    // P5.6: one grouped aggregate query across every user, not one row-fetch per user — same
    // "aggregate with the database, not in memory" principle as P4.4/S5.7.
    @Query(
            "SELECT c.user.id, COUNT(c) FROM Credential c "
                    + "WHERE c.deleted = false AND c.passwordChangedAt < :threshold "
                    + "GROUP BY c.user.id")
    List<Object[]> countStaleCredentialsByUser(@Param("threshold") Instant threshold);

    long countByUserIdAndDeletedFalse(Long userId);

    long countByUserIdAndDeletedFalseAndFavoriteTrue(Long userId);

    // P5.7 step "Aggregate with database queries and projections, not by loading entities into
    // memory" — one grouped COUNT, not a full-table load followed by an in-memory groupingBy.
    @Query(
            "SELECT c.category, COUNT(c) FROM Credential c "
                    + "WHERE c.user.id = :userId AND c.deleted = false GROUP BY c.category")
    List<Object[]> countByCategoryForUser(@Param("userId") Long userId);
}
