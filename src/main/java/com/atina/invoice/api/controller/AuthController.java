// ============================================
// FILE: controller/AuthController.java - FIXED VERSION
// ============================================

package com.atina.invoice.api.controller;

import com.atina.invoice.api.dto.request.LoginRequest;
import com.atina.invoice.api.dto.response.ApiResponse;
import com.atina.invoice.api.dto.response.LoginResponse;
import com.atina.invoice.api.model.User;
import com.atina.invoice.api.model.Tenant;
import com.atina.invoice.api.security.JwtTokenProvider;
import com.atina.invoice.api.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

/**
 * Authentication controller - FIXED VERSION
 * Handles user login and JWT token generation with full tenant information
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "User authentication endpoints")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserService userService;

    /**
     * User login
     *
     * POST /api/v1/auth/login
     *
     * @param request Login credentials
     * @return JWT token and user info with tenant details
     */
    @PostMapping("/login")
    @Operation(
            summary = "User login",
            description = "Authenticate user and receive JWT token with tenant information"
    )
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("Login attempt for user: {}", request.getUsername());

        long start = System.currentTimeMillis();

        try {
            // Authenticate user
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getUsername(),
                            request.getPassword()
                    )
            );

            // Generate JWT token
            String token = jwtTokenProvider.generateToken(authentication);

            // Get token expiration
            Instant expiresAt = jwtTokenProvider.getExpirationFromToken(token);

            // Get full user details with tenant
            User user = userService.findByUsername(request.getUsername())
                    .orElseThrow(() -> new BadCredentialsException("User not found"));

            // Update last login
            userService.updateLastLogin(request.getUsername());

            // Build tenant info
            Tenant tenant = user.getTenant();
            LoginResponse.TenantInfo tenantInfo = LoginResponse.TenantInfo.builder()
                    .id(tenant.getId())
                    .code(tenant.getTenantCode())
                    .name(tenant.getTenantName())
                    .subscriptionTier(tenant.getSubscriptionTier())
                    .maxApiCallsPerMonth(tenant.getMaxApiCallsPerMonth())
                    .enabled(tenant.isEnabled())
                    .build();

            // Build response with full user and tenant info
            LoginResponse response = LoginResponse.builder()
                    .token(token)
                    .expiresAt(expiresAt)
                    .username(user.getUsername())
                    .fullName(user.getFullName())
                    .email(user.getEmail())
                    .role(user.getRole())
                    .tenant(tenantInfo)
                    .build();

            long duration = System.currentTimeMillis() - start;

            log.info("Login successful for user: {} (tenant: {}, role: {}) ({}ms)",
                    user.getUsername(), tenant.getTenantCode(), user.getRole(), duration);

            return ApiResponse.success(response, MDC.get("correlationId"), duration);

        } catch (BadCredentialsException e) {
            log.warn("Login failed for user: {} - Invalid credentials", request.getUsername());
            throw new BadCredentialsException("Invalid username or password");

        } catch (AuthenticationException e) {
            log.error("Login failed for user: {} - {}", request.getUsername(), e.getMessage());
            throw new BadCredentialsException("Authentication failed: " + e.getMessage());
        }
    }

    /**
     * Token refresh
     *
     * POST /api/v1/auth/refresh
     *
     * @return New JWT token
     */
    @PostMapping("/refresh")
    @Operation(
            summary = "Refresh JWT token",
            description = "Get a new JWT token using valid existing token"
    )
    public ApiResponse<LoginResponse> refresh(Authentication authentication) {
        log.info("Token refresh for user: {}", authentication.getName());

        long start = System.currentTimeMillis();

        try {
            // Generate new token
            String newToken = jwtTokenProvider.generateToken(authentication);
            Instant newExpiresAt = jwtTokenProvider.getExpirationFromToken(newToken);

            // Get full user details with tenant
            User user = userService.findByUsername(authentication.getName())
                    .orElseThrow(() -> new BadCredentialsException("User not found"));

            // Build tenant info
            Tenant tenant = user.getTenant();
            LoginResponse.TenantInfo tenantInfo = LoginResponse.TenantInfo.builder()
                    .id(tenant.getId())
                    .code(tenant.getTenantCode())
                    .name(tenant.getTenantName())
                    .subscriptionTier(tenant.getSubscriptionTier())
                    .maxApiCallsPerMonth(tenant.getMaxApiCallsPerMonth())
                    .enabled(tenant.isEnabled())
                    .build();

            // Build response
            LoginResponse response = LoginResponse.builder()
                    .token(newToken)
                    .expiresAt(newExpiresAt)
                    .username(user.getUsername())
                    .fullName(user.getFullName())
                    .email(user.getEmail())
                    .role(user.getRole())
                    .tenant(tenantInfo)
                    .build();

            long duration = System.currentTimeMillis() - start;

            log.info("Token refreshed for user: {} ({}ms)", authentication.getName(), duration);

            return ApiResponse.success(response, MDC.get("correlationId"), duration);

        } catch (Exception e) {
            log.error("Token refresh failed for user: {} - {}", authentication.getName(), e.getMessage());
            throw new BadCredentialsException("Token refresh failed: " + e.getMessage());
        }
    }

    /**
     * Logout
     *
     * POST /api/v1/auth/logout
     *
     * @return Success message
     */
    @PostMapping("/logout")
    @Operation(
            summary = "User logout",
            description = "Logout user (client should discard token)"
    )
    public ApiResponse<String> logout(Authentication authentication) {
        log.info("Logout for user: {}", authentication.getName());

        // In a stateless JWT setup, logout is handled client-side by discarding the token
        // If you want server-side token blacklisting, implement it here

        return ApiResponse.success("Logout successful");
    }

    /**
     * Get current user info
     *
     * GET /api/v1/auth/me
     *
     * @return Current user information with tenant details
     */
    @GetMapping("/me")
    @Operation(
            summary = "Get current user",
            description = "Get information about currently authenticated user"
    )
    public ApiResponse<LoginResponse> getCurrentUser(Authentication authentication) {
        log.info("Get current user: {}", authentication.getName());

        long start = System.currentTimeMillis();

        try {
            // Get full user details with tenant
            User user = userService.findByUsername(authentication.getName())
                    .orElseThrow(() -> new BadCredentialsException("User not found"));

            // Build tenant info
            Tenant tenant = user.getTenant();
            LoginResponse.TenantInfo tenantInfo = LoginResponse.TenantInfo.builder()
                    .id(tenant.getId())
                    .code(tenant.getTenantCode())
                    .name(tenant.getTenantName())
                    .subscriptionTier(tenant.getSubscriptionTier())
                    .maxApiCallsPerMonth(tenant.getMaxApiCallsPerMonth())
                    .enabled(tenant.isEnabled())
                    .build();

            // Build response (without token since this is not a login)
            LoginResponse response = LoginResponse.builder()
                    .username(user.getUsername())
                    .fullName(user.getFullName())
                    .email(user.getEmail())
                    .role(user.getRole())
                    .tenant(tenantInfo)
                    .build();

            long duration = System.currentTimeMillis() - start;

            return ApiResponse.success(response, MDC.get("correlationId"), duration);

        } catch (Exception e) {
            log.error("Get current user failed for: {} - {}", authentication.getName(), e.getMessage());
            throw new RuntimeException("Failed to get user info: " + e.getMessage());
        }
    }
}
