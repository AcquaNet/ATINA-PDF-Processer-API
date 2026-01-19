package com.atina.invoice.api.controller;

import com.atina.invoice.api.model.Tenant;
import com.atina.invoice.api.model.User;
import com.atina.invoice.api.repository.TenantRepository;
import com.atina.invoice.api.repository.UserRepository;
import com.atina.invoice.api.security.JwtTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

/**
 * Clase base para tests de integración
 * Configura el ambiente de test y provee métodos helper
 */
@Slf4j
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class BaseControllerTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected TenantRepository tenantRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    protected JwtTokenProvider jwtTokenProvider;

    // Test tenants
    protected Tenant systemTenant;
    protected Tenant acmeTenant;
    protected Tenant globexTenant;

    // Test users
    protected User systemAdmin;
    protected User acmeAdmin;
    protected User acmeUser;
    protected User globexUser;

    // Test tokens
    protected String systemAdminToken;
    protected String acmeAdminToken;
    protected String acmeUserToken;
    protected String globexUserToken;

    @BeforeEach
    public void setUp() {
        // Limpiar datos previos (importante el orden por las FK)
        try {
            userRepository.deleteAll();
            userRepository.flush();
            tenantRepository.deleteAll();
            tenantRepository.flush();
        } catch (Exception e) {
            log.warn("Error cleaning database: {}", e.getMessage());
        }

        // Crear tenants de prueba
        systemTenant = createTenant("SYSTEM", "System Administration");
        acmeTenant = createTenant("ACME", "ACME Corporation");
        globexTenant = createTenant("GLOBEX", "Globex Corporation");

        // Crear usuarios de prueba
        systemAdmin = createUser("system-admin", "SYSTEM_ADMIN", systemTenant);
        acmeAdmin = createUser("acme-admin", "ADMIN", acmeTenant);
        acmeUser = createUser("acme-user", "USER", acmeTenant);
        globexUser = createUser("globex-user", "USER", globexTenant);

        // Generar tokens
        systemAdminToken = generateToken(systemAdmin);
        acmeAdminToken = generateToken(acmeAdmin);
        acmeUserToken = generateToken(acmeUser);
        globexUserToken = generateToken(globexUser);
    }

    /**
     * Crea un tenant de prueba
     */
    protected Tenant createTenant(String code, String name) {
        Tenant tenant = Tenant.builder()
                .tenantCode(code)
                .tenantName(name)
                .contactEmail(code.toLowerCase() + "@example.com")
                .enabled(true)
                .maxApiCallsPerMonth(10000L)
                .maxStorageMb(1000L)
                .subscriptionTier("PREMIUM")
                .createdAt(Instant.now())
                .build();
        return tenantRepository.save(tenant);
    }

    /**
     * Crea un usuario de prueba
     */
    protected User createUser(String username, String role, Tenant tenant) {
        User user = User.builder()
                .username(username)
                .password(passwordEncoder.encode("password123"))
                .email(username + "@example.com")
                .fullName(username.replace("-", " ").toUpperCase())
                .role(role)
                .tenant(tenant)
                .enabled(true)
                .createdAt(Instant.now())
                .build();
        return userRepository.save(user);
    }

    /**
     * Genera un token JWT para un usuario
     */
    protected String generateToken(User user) {
        return jwtTokenProvider.generateToken(
                user.getUsername(),
                user.getTenant().getId(),
                user.getTenant().getTenantCode(),
                user.getRole()
        );
    }

    /**
     * Convierte un objeto a JSON
     */
    protected String toJson(Object obj) throws Exception {
        return objectMapper.writeValueAsString(obj);
    }

    /**
     * Convierte JSON a objeto
     */
    protected <T> T fromJson(String json, Class<T> clazz) throws Exception {
        return objectMapper.readValue(json, clazz);
    }
}
