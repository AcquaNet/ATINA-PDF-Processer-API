package com.atina.invoice.api.dto.request;

import com.atina.invoice.api.model.enums.EmailType;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request para crear una cuenta de email
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateEmailAccountRequest {

    @NotBlank(message = "Email address is required")
    @Email(message = "Email address must be valid")
    private String emailAddress;

    @NotNull(message = "Email type is required")
    private EmailType emailType;

    @NotBlank(message = "Host is required")
    private String host;

    @NotNull(message = "Port is required")
    @Min(value = 1, message = "Port must be between 1 and 65535")
    @Max(value = 65535, message = "Port must be between 1 and 65535")
    private Integer port;

    @NotBlank(message = "Username is required")
    private String username;

    @NotBlank(message = "Password is required")
    @Size(min = 6, message = "Password must be at least 6 characters")
    private String password;

    @Builder.Default
    private Boolean useSsl = true;

    @Builder.Default
    private Boolean pollingEnabled = true;

    @NotNull(message = "Polling interval is required")
    @Min(value = 1, message = "Polling interval must be at least 1 minute")
    @Max(value = 1440, message = "Polling interval must be at most 1440 minutes (24 hours)")
    @Builder.Default
    private Integer pollingIntervalMinutes = 10;

    @NotBlank(message = "Folder name is required")
    @Builder.Default
    private String folderName = "INBOX";

    private String description;

    @Builder.Default
    private Boolean enabled = true;
}
