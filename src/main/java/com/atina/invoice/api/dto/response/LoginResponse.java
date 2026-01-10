package com.atina.invoice.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Login response with JWT token")
public class LoginResponse {

    @Schema(description = "JWT access token", required = true)
    private String token;

    @Schema(description = "Token type", required = true, example = "Bearer")
    @Builder.Default
    private String type = "Bearer";

    @Schema(description = "Username", required = true)
    private String username;

    @Schema(description = "Token expiration time")
    private Instant expiresAt;
}