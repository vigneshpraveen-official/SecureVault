package com.securevault.user;

import org.springframework.data.jpa.domain.Specification;

/** Same composable-Specification pattern as vault.CredentialSpecifications (D-12/ADR-021). */
public final class UserSpecifications {

    private UserSpecifications() {}

    public static Specification<User> emailOrNameContains(String term) {
        return (root, query, cb) -> {
            String pattern = "%" + term.toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("email")), pattern),
                    cb.like(cb.lower(root.get("fullName")), pattern));
        };
    }
}
