package com.atina.invoice.api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailSenderRuleResponse {
    private Long id;
    private Long tenantId;
    private String tenantCode;
    private Long emailAccountId;
    private String emailAccountAddress;
    private String senderEmail;
    private String senderId;
    private String senderName;
    private String templateEmailReceived;
    private String templateEmailProcessed;
    private Boolean autoReplyEnabled;
    private Boolean processEnabled;
    private String description;
    private Boolean enabled;
    private Instant createdAt;
    private Instant updatedAt;
    private List<AttachmentProcessingRuleResponse> attachmentRules;
}
