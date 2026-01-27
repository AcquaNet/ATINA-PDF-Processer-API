package com.atina.invoice.api.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request para crear una regla de sender
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateEmailSenderRuleRequest {

    @NotNull(message = "Email account ID is required")
    private Long emailAccountId;

    @NotBlank(message = "Sender email is required")
    @Email(message = "Sender email must be valid")
    private String senderEmail;

    @NotBlank(message = "Sender ID is required")
    private String senderId;

    private String senderName;

    // Templates
    private String templateEmailReceived;
    private String templateEmailProcessed;

    // Email subjects
    private String subjectEmailReceived;
    private String subjectEmailProcessed;

    // Notification email (CC)
    private String notificationEmail;

    // Configuración
    @Builder.Default
    private Boolean autoReplyEnabled = true;

    @Builder.Default
    private Boolean processEnabled = true;

    private String description;

    @Builder.Default
    private Boolean enabled = true;
}
