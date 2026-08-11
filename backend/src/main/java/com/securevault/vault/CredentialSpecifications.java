package com.securevault.vault;

import org.springframework.data.jpa.domain.Specification;

/**
 * Composed with plain if-not-null `.and(...)` chaining in CredentialServiceImpl, not
 * Specification.allOf(...) — explicit null checks at the call site are unambiguous regardless of
 * the exact Spring Data JPA minor version's null-handling behavior in allOf/and (P4.5/M-34). Never
 * build any of this by string concatenation.
 */
public final class CredentialSpecifications {

    private CredentialSpecifications() {}

    /** Always ANDed first, in every query this repository ever runs for a user (P4.3, P4.5). */
    public static Specification<Credential> ownedByAndNotDeleted(Long userId) {
        return (root, query, cb) ->
                cb.and(
                        cb.equal(root.get("user").get("id"), userId),
                        cb.isFalse(root.get("deleted")));
    }

    public static Specification<Credential> hasCategory(Category category) {
        return (root, query, cb) -> cb.equal(root.get("category"), category);
    }

    public static Specification<Credential> titleContains(String title) {
        return (root, query, cb) ->
                cb.like(cb.lower(root.get("title")), "%" + title.toLowerCase() + "%");
    }

    public static Specification<Credential> usernameContains(String username) {
        return (root, query, cb) ->
                cb.like(cb.lower(root.get("username")), "%" + username.toLowerCase() + "%");
    }

    public static Specification<Credential> websiteContains(String website) {
        return (root, query, cb) ->
                cb.like(cb.lower(root.get("websiteUrl")), "%" + website.toLowerCase() + "%");
    }
}
