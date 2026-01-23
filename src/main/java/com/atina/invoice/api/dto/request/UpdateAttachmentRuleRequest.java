package com.atina.invoice.api.dto.request;

import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateAttachmentRuleRequest {
    @Min(value = 1, message = "Rule order must be at least 1")
    private Integer ruleOrder;
    private String fileNameRegex;
    private String source;
    private String destination;
    private String processingMethod;
    private String description;
    private Boolean enabled;
}
