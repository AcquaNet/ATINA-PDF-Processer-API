package com.atina.invoice.api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttachmentProcessingRuleResponse {
    private Long id;
    private Long senderRuleId;
    private Integer ruleOrder;
    private String fileNameRegex;
    private String source;
    private String destination;
    private String processingMethod;
    private String description;
    private Boolean enabled;
    private Instant createdAt;
    private Instant updatedAt;
}
