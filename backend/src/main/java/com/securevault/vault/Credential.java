package com.securevault.vault;

import com.securevault.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/**
 * Matches V1__init.sql + V2__add_password_changed_at.sql. category/search/filter behaviour arrives
 * in S1.5 — every credential defaults to OTHER here. strengthScore/passwordChangedAt are set by
 * CredentialServiceImpl at create time and whenever the password actually changes (S3.3) — never by
 * the mapper.
 */
@Entity
@Table(name = "credentials")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Credential {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(length = 150)
    private String username;

    @Column(name = "encrypted_password", nullable = false, columnDefinition = "TEXT")
    private String encryptedPassword;

    @Column(name = "website_url", length = 255)
    private String websiteUrl;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private Category category = Category.OTHER;

    @Column(nullable = false)
    @Builder.Default
    private boolean favorite = false;

    // Short, not Integer — the DB column is SMALLINT (V1__init.sql); ddl-auto=validate requires
    // an exact type match. @JdbcTypeCode pins it to SMALLINT explicitly rather than relying on
    // Hibernate's default Java-type inference, which validated this as INTEGER without it.
    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "strength_score")
    private Short strengthScore;

    @Column(name = "password_changed_at", nullable = false)
    private Instant passwordChangedAt;

    @Column(nullable = false)
    @Builder.Default
    private boolean deleted = false;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
