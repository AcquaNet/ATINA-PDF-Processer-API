package com.atina.invoice.api.security;

import com.atina.invoice.api.model.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * JWT Token Provider - Enhanced with multi-tenancy support
 * Includes tenant information in JWT claims for tenant isolation
 */
@Slf4j
@Component
public class JwtTokenProvider {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration}")
    private long jwtExpiration;

    @Value("${jwt.issuer}")
    private String jwtIssuer;

    private SecretKey key;

    @PostConstruct
    public void init() {
        this.key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        log.info("JWT Token Provider initialized with multi-tenancy support");
    }

    /**
     * Generate token from Authentication
     */
    public String generateToken(Authentication authentication) {
        org.springframework.security.core.userdetails.User userDetails =
                (org.springframework.security.core.userdetails.User) authentication.getPrincipal();
        return generateToken(userDetails.getUsername(), null, null);
    }

    /**
     * Generate token with tenant information
     */
    public String generateToken(User user) {
        return generateToken(
                user.getUsername(),
                user.getTenant().getId(),
                user.getTenant().getTenantCode()
        );
    }

    /**
     * Generate token with username, tenantId, and tenantCode
     */
    public String generateToken(String username, Long tenantId, String tenantCode) {
        Instant now = Instant.now();
        Instant expiryDate = now.plusMillis(jwtExpiration);

        JwtBuilder builder = Jwts.builder()
                .subject(username)
                .issuer(jwtIssuer)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiryDate))
                .signWith(key);

        // Add tenant claims if provided
        if (tenantId != null) {
            builder.claim("tenantId", tenantId);
        }
        if (tenantCode != null) {
            builder.claim("tenantCode", tenantCode);
        }

        return builder.compact();
    }

    /**
     * Get username from token
     */
    public String getUsernameFromToken(String token) {
        Claims claims = getClaims(token);
        return claims.getSubject();
    }

    /**
     * Get tenant ID from token
     */
    public Long getTenantIdFromToken(String token) {
        Claims claims = getClaims(token);
        Object tenantId = claims.get("tenantId");
        if (tenantId == null) {
            return null;
        }
        // Handle both Integer and Long
        if (tenantId instanceof Integer) {
            return ((Integer) tenantId).longValue();
        }
        return (Long) tenantId;
    }

    /**
     * Get tenant code from token
     */
    public String getTenantCodeFromToken(String token) {
        Claims claims = getClaims(token);
        return (String) claims.get("tenantCode");
    }

    /**
     * Get all claims from token
     */
    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Validate token
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (SecurityException ex) {
            log.error("Invalid JWT signature");
        } catch (MalformedJwtException ex) {
            log.error("Invalid JWT token");
        } catch (ExpiredJwtException ex) {
            log.error("Expired JWT token");
        } catch (UnsupportedJwtException ex) {
            log.error("Unsupported JWT token");
        } catch (IllegalArgumentException ex) {
            log.error("JWT claims string is empty");
        }
        return false;
    }

    /**
     * Get expiration from token
     */
    public Instant getExpirationFromToken(String token) {
        Claims claims = getClaims(token);
        return claims.getExpiration().toInstant();
    }
}
