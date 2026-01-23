package com.atina.invoice.api.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateAttachmentRuleRequest {
    
    @NotNull(message = "Rule order is required")
    @Min(value = 1, message = "Rule order must be at least 1")
    private Integer ruleOrder;
    
    @NotBlank(message = "File name regex is required")
    private String fileNameRegex;
    
    @NotBlank(message = "Source is required")
    private String source;
    
    @NotBlank(message = "Destination is required")
    private String destination;
    
    private String processingMethod;
    private String description;
    
    @Builder.Default
    private Boolean enabled = true;
}
