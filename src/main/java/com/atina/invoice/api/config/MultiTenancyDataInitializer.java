package com.atina.invoice.api.config;

import com.atina.invoice.api.model.Tenant;
import com.atina.invoice.api.model.User;
import com.atina.invoice.api.repository.TenantRepository;
import com.atina.invoice.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;

/**
 * Data Initializer for Multi-Tenancy
 * Creates demo tenants and users on application startup
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class MultiTenancyDataInitializer {

    private final PasswordEncoder passwordEncoder;

    @Bean
    CommandLineRunner initMultiTenancyData(TenantRepository tenantRepository, 
                                           UserRepository userRepository) {
        return args -> {
            log.info("Initializing multi-tenancy data...");

            // Crear tenant SYSTEM
            Tenant systemTenant = createTenantIfNotExists(
                    tenantRepository, "SYSTEM", "System Administration",
                    "admin@system.internal", "UNLIMITED", null, null
            );

            // Crear super admin
            createUserIfNotExists(
                    userRepository, "superadmin", "superadmin123",
                    "System Super Administrator", "superadmin@system.internal",
                    "SYSTEM_ADMIN", systemTenant
            );



            // Create tenants if they don't exist
            Tenant acmeTenant = createTenantIfNotExists(
                tenantRepository,
                "ACME",
                "ACME Corporation",
                "admin@acme.com",
                "PREMIUM",
                1000000L,
                10000L
            );

            Tenant globexTenant = createTenantIfNotExists(
                tenantRepository,
                "GLOBEX",
                "Globex Corporation",
                "admin@globex.com",
                "BASIC",
                100000L,
                5000L
            );

            Tenant initechTenant = createTenantIfNotExists(
                tenantRepository,
                "INITECH",
                "Initech Inc.",
                "admin@initech.com",
                "FREE",
                10000L,
                1000L
            );

            // Create users for each tenant
            createUserIfNotExists(
                userRepository,
                "acme-admin",
                "admin123",
                "ACME Administrator",
                "admin@acme.com",
                "ADMIN",
                acmeTenant
            );

            createUserIfNotExists(
                userRepository,
                "acme-user-1",
                "user123",
                "ACME User",
                "user@acme.com",
                "USER",
                acmeTenant
            );

            createUserIfNotExists(
                userRepository,
                "acme-user-2",
                "user123",
                "ACME User",
                "user@acme.com",
                "USER",
                acmeTenant
            );

            createUserIfNotExists(
                userRepository,
                "globex-admin",
                "admin123",
                "Globex Administrator",
                "admin@globex.com",
                "ADMIN",
                globexTenant
            );

            createUserIfNotExists(
                userRepository,
                "initech-user",
                "user123",
                "Initech User",
                "user@initech.com",
                "USER",
                initechTenant
            );

            log.info("Multi-tenancy data initialization completed");
            log.info("=".repeat(80));
            log.info("Demo Tenants Created:");
            log.info("  1. ACME Corporation (PREMIUM) - 1M API calls/month");
            log.info("  2. Globex Corporation (BASIC) - 100K API calls/month");
            log.info("  3. Initech Inc. (FREE) - 10K API calls/month");
            log.info("=".repeat(80));
            log.info("Demo Users Created:");
            log.info("  • acme-admin / admin123 (ACME - ADMIN)");
            log.info("  • acme-user-1 / user123 (ACME - USER)");
            log.info("  • acme-user-2 / user123 (ACME - USER)");
            log.info("  • globex-admin / admin123 (GLOBEX - ADMIN)");
            log.info("  • initech-user / user123 (INITECH - USER)");
            log.info("=".repeat(80));
        };
    }

    private Tenant createTenantIfNotExists(
            TenantRepository tenantRepository,
            String tenantCode,
            String tenantName,
            String contactEmail,
            String subscriptionTier,
            Long maxApiCallsPerMonth,
            Long maxStorageMb) {

        return tenantRepository.findByTenantCode(tenantCode)
                .orElseGet(() -> {
                    Tenant tenant = Tenant.builder()
                            .tenantCode(tenantCode)
                            .tenantName(tenantName)
                            .contactEmail(contactEmail)
                            .subscriptionTier(subscriptionTier)
                            .maxApiCallsPerMonth(maxApiCallsPerMonth)
                            .maxStorageMb(maxStorageMb)
                            .enabled(true)
                            .createdAt(Instant.now())
                            .updatedAt(Instant.now())
                            .build();

                    Tenant saved = tenantRepository.save(tenant);
                    log.info("Created tenant: {} ({})", tenantName, tenantCode);
                    return saved;
                });
    }

    private void createUserIfNotExists(
            UserRepository userRepository,
            String username,
            String password,
            String fullName,
            String email,
            String role,
            Tenant tenant) {

        if (!userRepository.existsByUsername(username)) {
            User user = User.builder()
                    .username(username)
                    .password(passwordEncoder.encode(password))
                    .fullName(fullName)
                    .email(email)
                    .role(role)
                    .tenant(tenant)
                    .enabled(true)
                    .createdAt(Instant.now())
                    .build();

            userRepository.save(user);
            log.info("Created user: {} for tenant: {} (role: {})", 
                     username, tenant.getTenantCode(), role);
        }
    }
}
