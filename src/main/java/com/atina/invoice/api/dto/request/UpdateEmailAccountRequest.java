package com.atina.invoice.api.dto.request;

import com.atina.invoice.api.model.enums.EmailType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request para actualizar una cuenta de email
 * Todos los campos son opcionales
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateEmailAccountRequest {

    @Email(message = "Email address must be valid")
    private String emailAddress;

    private EmailType emailType;

    private String host;

    @Min(value = 1, message = "Port must be between 1 and 65535")
    @Max(value = 65535, message = "Port must be between 1 and 65535")
    private Integer port;

    private String username;

    @Size(min = 6, message = "Password must be at least 6 characters")
    private String password;

    private Boolean useSsl;

    private Boolean pollingEnabled;

    @Min(value = 1, message = "Polling interval must be at least 1 minute")
    @Max(value = 1440, message = "Polling interval must be at most 1440 minutes (24 hours)")
    private Integer pollingIntervalMinutes;

    private String folderName;

    private String description;

    private Boolean enabled;
}
