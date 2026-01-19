package com.atina.invoice.api.controller;


import com.atina.invoice.api.dto.request.CreateUserRequest;
import com.atina.invoice.api.dto.request.UpdateUserRequest;
import com.atina.invoice.api.dto.response.UserResponse;
import com.atina.invoice.api.model.Tenant;
import com.atina.invoice.api.model.User;
import com.atina.invoice.api.repository.TenantRepository;
import com.atina.invoice.api.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * REST Controller para gestión de usuarios
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "User management endpoints")
@SecurityRequirement(name = "bearer-jwt")
public class UserController {

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * GET /api/v1/users
     * Listar todos los usuarios (paginado)
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM_ADMIN')")
    @Operation(summary = "List all users", description = "Get paginated list of users")
    public ResponseEntity<Map<String, Object>> getAllUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDirection
    ) {
        log.info("Fetching users - page: {}, size: {}, sortBy: {}", page, size, sortBy);

        Sort sort = sortDirection.equalsIgnoreCase("desc")
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();

        Pageable pageable = PageRequest.of(page, size, sort);
        Page<User> usersPage = userRepository.findAll(pageable);

        Map<String, Object> response = new HashMap<>();
        response.put("users", usersPage.getContent().stream()
                .map(this::mapToUserResponse)
                .toList());
        response.put("currentPage", usersPage.getNumber());
        response.put("totalItems", usersPage.getTotalElements());
        response.put("totalPages", usersPage.getTotalPages());

        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/v1/users/{id}
     * Obtener un usuario por ID
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM_ADMIN')")
    @Operation(summary = "Get user by ID", description = "Get a specific user by their ID")
    public ResponseEntity<UserResponse> getUserById(@PathVariable Long id) {
        log.info("Fetching user with id: {}", id);

        return userRepository.findById(id)
                .map(user -> ResponseEntity.ok(mapToUserResponse(user)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * GET /api/v1/users/username/{username}
     * Obtener un usuario por username
     */
    @GetMapping("/username/{username}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM_ADMIN')")
    @Operation(summary = "Get user by username", description = "Get a specific user by their username")
    public ResponseEntity<UserResponse> getUserByUsername(@PathVariable String username) {
        log.info("Fetching user with username: {}", username);

        return userRepository.findByUsername(username)
                .map(user -> ResponseEntity.ok(mapToUserResponse(user)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * POST /api/v1/users
     * Crear un nuevo usuario
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM_ADMIN')")
    @Operation(summary = "Create user", description = "Create a new user")
    public ResponseEntity<?> createUser(@Valid @RequestBody CreateUserRequest request) {
        log.info("Creating user: {}", request.getUsername());

        // Validar si el usuario ya existe
        if (userRepository.existsByUsername(request.getUsername())) {
            return ResponseEntity
                    .badRequest()
                    .body(Map.of("error", "Username already exists"));
        }

        // Buscar el tenant
        Tenant tenant = tenantRepository.findByTenantCode(request.getTenantCode())
                .orElseThrow(() -> new RuntimeException("Tenant not found: " + request.getTenantCode()));

        // Crear el usuario
        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .email(request.getEmail())
                .fullName(request.getFullName())
                .role(request.getRole())
                .tenant(tenant)
                .enabled(request.getEnabled())
                .createdAt(Instant.now())
                .build();

        User savedUser = userRepository.save(user);
        log.info("User created successfully: {}", savedUser.getId());

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(mapToUserResponse(savedUser));
    }

    /**
     * PUT /api/v1/users/{id}
     * Actualizar un usuario existente
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM_ADMIN')")
    @Operation(summary = "Update user", description = "Update an existing user")
    public ResponseEntity<?> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRequest request
    ) {
        log.info("Updating user with id: {}", id);

        return userRepository.findById(id)
                .map(user -> {
                    // Actualizar solo los campos proporcionados
                    if (request.getEmail() != null) {
                        user.setEmail(request.getEmail());
                    }
                    if (request.getFullName() != null) {
                        user.setFullName(request.getFullName());
                    }
                    if (request.getRole() != null) {
                        user.setRole(request.getRole());
                    }
                    if (request.getEnabled() != null) {
                        user.setEnabled(request.getEnabled());
                    }
                    if (request.getPassword() != null && !request.getPassword().isEmpty()) {
                        user.setPassword(passwordEncoder.encode(request.getPassword()));
                    }

                    User updatedUser = userRepository.save(user);
                    log.info("User updated successfully: {}", updatedUser.getId());

                    return ResponseEntity.ok(mapToUserResponse(updatedUser));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * DELETE /api/v1/users/{id}
     * Eliminar un usuario
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(summary = "Delete user", description = "Delete a user (SYSTEM_ADMIN only)")
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        log.info("Deleting user with id: {}", id);

        return userRepository.findById(id)
                .map(user -> {
                    userRepository.delete(user);
                    log.info("User deleted successfully: {}", id);
                    return ResponseEntity.ok(Map.of("message", "User deleted successfully"));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * PATCH /api/v1/users/{id}/disable
     * Deshabilitar un usuario (soft delete)
     */
    @PatchMapping("/{id}/disable")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM_ADMIN')")
    @Operation(summary = "Disable user", description = "Disable a user without deleting")
    public ResponseEntity<?> disableUser(@PathVariable Long id) {
        log.info("Disabling user with id: {}", id);

        return userRepository.findById(id)
                .map(user -> {
                    user.setEnabled(false);
                    User updatedUser = userRepository.save(user);
                    log.info("User disabled successfully: {}", id);
                    return ResponseEntity.ok(mapToUserResponse(updatedUser));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * PATCH /api/v1/users/{id}/enable
     * Habilitar un usuario
     */
    @PatchMapping("/{id}/enable")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM_ADMIN')")
    @Operation(summary = "Enable user", description = "Enable a disabled user")
    public ResponseEntity<?> enableUser(@PathVariable Long id) {
        log.info("Enabling user with id: {}", id);

        return userRepository.findById(id)
                .map(user -> {
                    user.setEnabled(true);
                    User updatedUser = userRepository.save(user);
                    log.info("User enabled successfully: {}", id);
                    return ResponseEntity.ok(mapToUserResponse(updatedUser));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * GET /api/v1/users/tenant/{tenantCode}
     * Obtener usuarios por tenant
     */
    @GetMapping("/tenant/{tenantCode}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM_ADMIN')")
    @Operation(summary = "Get users by tenant", description = "Get all users belonging to a specific tenant")
    public ResponseEntity<?> getUsersByTenant(@PathVariable String tenantCode) {
        log.info("Fetching users for tenant: {}", tenantCode);

        return tenantRepository.findByTenantCode(tenantCode)
                .map(tenant -> {
                    var users = userRepository.findByTenant(tenant).stream()
                            .map(this::mapToUserResponse)
                            .toList();
                    return ResponseEntity.ok(users);
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // ========================================
    // Helper Methods
    // ========================================

    private UserResponse mapToUserResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole())
                .enabled(user.isEnabled())
                .createdAt(user.getCreatedAt())
                .lastLoginAt(user.getLastLoginAt())
                .tenant(UserResponse.TenantInfo.builder()
                        .id(user.getTenant().getId())
                        .tenantCode(user.getTenant().getTenantCode())
                        .tenantName(user.getTenant().getTenantName())
                        .build())
                .build();
    }
}
