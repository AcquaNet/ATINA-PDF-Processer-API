package com.atina.invoice.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Login request")
public class LoginRequest {

    @NotBlank(message = "Username is required")
    @Schema(description = "Username", required = true, example = "admin")
    private String username;

    @NotBlank(message = "Password is required")
    @Schema(description = "Password", required = true, example = "admin123")
    private String password;
}