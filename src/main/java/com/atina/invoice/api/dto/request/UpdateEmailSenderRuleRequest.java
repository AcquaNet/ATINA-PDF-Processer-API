package com.atina.invoice.api.dto.request;

import jakarta.validation.constraints.Email;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateEmailSenderRuleRequest {
    @Email(message = "Sender email must be valid")
    private String senderEmail;
    private String senderId;
    private String senderName;
    private String templateEmailReceived;
    private String templateEmailProcessed;
    private Boolean autoReplyEnabled;
    private Boolean processEnabled;
    private String inboundPath;
    private String metadataPath;
    private String description;
    private Boolean enabled;
}
