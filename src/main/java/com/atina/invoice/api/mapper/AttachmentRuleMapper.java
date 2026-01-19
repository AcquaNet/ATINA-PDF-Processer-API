package com.atina.invoice.api.mapper;

import com.atina.invoice.api.dto.request.CreateAttachmentRuleRequest;
import com.atina.invoice.api.dto.request.UpdateAttachmentRuleRequest;
import com.atina.invoice.api.dto.response.AttachmentProcessingRuleResponse;
import com.atina.invoice.api.model.AttachmentProcessingRule;
import com.atina.invoice.api.model.EmailSenderRule;
import org.springframework.stereotype.Component;

/**
 * Mapper para AttachmentProcessingRule
 */
@Component
public class AttachmentRuleMapper {

    /**
     * Convertir CreateRequest a Entity
     */
    public AttachmentProcessingRule toEntity(CreateAttachmentRuleRequest request, EmailSenderRule senderRule) {
        return AttachmentProcessingRule.builder()
                .senderRule(senderRule)
                .ruleOrder(request.getRuleOrder())
                .fileNameRegex(request.getFileNameRegex())
                .source(request.getSource())
                .destination(request.getDestination())
                .processingMethod(request.getProcessingMethod())
                .description(request.getDescription())
                .enabled(request.getEnabled())
                .build();
    }

    /**
     * Actualizar entity desde UpdateRequest
     */
    public void updateEntity(AttachmentProcessingRule entity, UpdateAttachmentRuleRequest request) {
        if (request.getRuleOrder() != null) {
            entity.setRuleOrder(request.getRuleOrder());
        }
        if (request.getFileNameRegex() != null) {
            entity.setFileNameRegex(request.getFileNameRegex());
        }
        if (request.getSource() != null) {
            entity.setSource(request.getSource());
        }
        if (request.getDestination() != null) {
            entity.setDestination(request.getDestination());
        }
        if (request.getProcessingMethod() != null) {
            entity.setProcessingMethod(request.getProcessingMethod());
        }
        if (request.getDescription() != null) {
            entity.setDescription(request.getDescription());
        }
        if (request.getEnabled() != null) {
            entity.setEnabled(request.getEnabled());
        }
    }

    /**
     * Convertir Entity a Response
     */
    public AttachmentProcessingRuleResponse toResponse(AttachmentProcessingRule entity) {
        return AttachmentProcessingRuleResponse.builder()
                .id(entity.getId())
                .senderRuleId(entity.getSenderRule().getId())
                .ruleOrder(entity.getRuleOrder())
                .fileNameRegex(entity.getFileNameRegex())
                .source(entity.getSource())
                .destination(entity.getDestination())
                .processingMethod(entity.getProcessingMethod())
                .description(entity.getDescription())
                .enabled(entity.getEnabled())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
