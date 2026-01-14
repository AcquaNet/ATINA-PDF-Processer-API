package com.atina.invoice.api.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * User entity - Enhanced with multi-tenancy support
 * Each user belongs to a tenant (company/organization)
 */
@Entity
@Table(name = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String password;  // BCrypt encoded

    @Column
    private String email;

    @Column
    private String fullName;

    /**
     * Tenant this user belongs to
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    /**
     * User role within their tenant
     * Examples: "ADMIN", "USER", "VIEWER"
     */
    @Column(nullable = false, length = 50)
    private String role = "USER";

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    @Column(nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column
    private Instant lastLoginAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
