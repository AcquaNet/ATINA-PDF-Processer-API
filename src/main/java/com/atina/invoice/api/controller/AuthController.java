// ============================================
// FILE: controller/AuthController.java
// ============================================

package com.atina.invoice.api.controller;

import com.atina.invoice.api.dto.request.LoginRequest;
import com.atina.invoice.api.dto.response.ApiResponse;
import com.atina.invoice.api.dto.response.LoginResponse;
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
 * Authentication controller
 * Handles user login and JWT token generation
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
     * @return JWT token and user info
     */
    @PostMapping("/login")
    @Operation(
        summary = "User login",
        description = "Authenticate user and receive JWT token"
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
            
            // Update last login
            userService.updateLastLogin(request.getUsername());
            
            // Build response
            LoginResponse response = LoginResponse.builder()
                .token(token)
                .username(request.getUsername())
                .expiresAt(expiresAt)
                .build();
            
            long duration = System.currentTimeMillis() - start;
            
            log.info("Login successful for user: {} ({}ms)", request.getUsername(), duration);
            
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
     * Validate token (optional endpoint)
     * 
     * GET /api/v1/auth/validate
     * 
     * @return Token validity status
     */
    @GetMapping("/validate")
    @Operation(
        summary = "Validate JWT token",
        description = "Check if the provided JWT token is valid"
    )
    public ApiResponse<Boolean> validateToken() {
        log.debug("Token validation requested");
        
        // If request reaches here, token is valid (passed through JWT filter)
        return ApiResponse.success(true, MDC.get("correlationId"), 0L);
    }

    /**
     * Refresh token (optional endpoint)
     * 
     * POST /api/v1/auth/refresh
     * 
     * @return New JWT token
     */
    @PostMapping("/refresh")
    @Operation(
        summary = "Refresh JWT token",
        description = "Generate a new JWT token using the current valid token"
    )
    public ApiResponse<LoginResponse> refreshToken(Authentication authentication) {
        log.info("Token refresh requested for user: {}", authentication.getName());
        
        long start = System.currentTimeMillis();
        
        // Generate new token
        String newToken = jwtTokenProvider.generateToken(authentication);
        
        // Get expiration
        Instant expiresAt = jwtTokenProvider.getExpirationFromToken(newToken);
        
        // Build response
        LoginResponse response = LoginResponse.builder()
            .token(newToken)
            .username(authentication.getName())
            .expiresAt(expiresAt)
            .build();
        
        long duration = System.currentTimeMillis() - start;
        
        return ApiResponse.success(response, MDC.get("correlationId"), duration);
    }
}

// ============================================
// USAGE EXAMPLES
// ============================================

/*
1. LOGIN

Request:
POST /api/v1/auth/login
Content-Type: application/json

{
  "username": "admin",
  "password": "admin123"
}

Response:
{
  "success": true,
  "correlationId": "api-20240110-123456-a1b2c3d4",
  "timestamp": "2024-01-10T12:34:56.789Z",
  "duration": 150,
  "data": {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "type": "Bearer",
    "username": "admin",
    "expiresAt": "2024-01-11T12:34:56.789Z"
  }
}

2. VALIDATE TOKEN

Request:
GET /api/v1/auth/validate
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...

Response:
{
  "success": true,
  "data": true
}

3. REFRESH TOKEN

Request:
POST /api/v1/auth/refresh
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...

Response:
{
  "success": true,
  "data": {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "type": "Bearer",
    "username": "admin",
    "expiresAt": "2024-01-11T12:34:56.789Z"
  }
}

4. LOGIN FAILED

Request:
POST /api/v1/auth/login
{
  "username": "admin",
  "password": "wrongpassword"
}

Response:
{
  "success": false,
  "error": {
    "code": "UNAUTHORIZED",
    "message": "Invalid username or password",
    "path": "/api/v1/auth/login"
  }
}

5. CURL EXAMPLES

# Login
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'

# Save token
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' \
  | jq -r '.data.token')

# Use token
curl http://localhost:8080/api/v1/health \
  -H "Authorization: Bearer $TOKEN"

# Validate token
curl http://localhost:8080/api/v1/auth/validate \
  -H "Authorization: Bearer $TOKEN"

# Refresh token
curl -X POST http://localhost:8080/api/v1/auth/refresh \
  -H "Authorization: Bearer $TOKEN"
*/
