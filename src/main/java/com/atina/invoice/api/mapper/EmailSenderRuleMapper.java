package com.atina.invoice.api.mapper;

import com.atina.invoice.api.dto.request.CreateEmailSenderRuleRequest;
import com.atina.invoice.api.dto.request.UpdateEmailSenderRuleRequest;
import com.atina.invoice.api.dto.response.EmailSenderRuleResponse;
import com.atina.invoice.api.model.EmailAccount;
import com.atina.invoice.api.model.EmailSenderRule;
import com.atina.invoice.api.model.Tenant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

/**
 * Mapper para EmailSenderRule
 */
@Component
@RequiredArgsConstructor
public class EmailSenderRuleMapper {

    private final AttachmentRuleMapper attachmentRuleMapper;

    /**
     * Convertir CreateRequest a Entity
     */
    public EmailSenderRule toEntity(CreateEmailSenderRuleRequest request, Tenant tenant, EmailAccount emailAccount) {
        return EmailSenderRule.builder()
                .tenant(tenant)
                .emailAccount(emailAccount)
                .senderEmail(request.getSenderEmail())
                .senderId(request.getSenderId())
                .senderName(request.getSenderName())
                .templateEmailReceived(request.getTemplateEmailReceived())
                .templateEmailProcessed(request.getTemplateEmailProcessed())
                .autoReplyEnabled(request.getAutoReplyEnabled())
                .processEnabled(request.getProcessEnabled())
                .description(request.getDescription())
                .enabled(request.getEnabled())
                .build();
    }

    /**
     * Actualizar entity desde UpdateRequest
     */
    public void updateEntity(EmailSenderRule entity, UpdateEmailSenderRuleRequest request) {
        if (request.getSenderEmail() != null) {
            entity.setSenderEmail(request.getSenderEmail());
        }
        if (request.getSenderId() != null) {
            entity.setSenderId(request.getSenderId());
        }
        if (request.getSenderName() != null) {
            entity.setSenderName(request.getSenderName());
        }
        if (request.getTemplateEmailReceived() != null) {
            entity.setTemplateEmailReceived(request.getTemplateEmailReceived());
        }
        if (request.getTemplateEmailProcessed() != null) {
            entity.setTemplateEmailProcessed(request.getTemplateEmailProcessed());
        }
        if (request.getAutoReplyEnabled() != null) {
            entity.setAutoReplyEnabled(request.getAutoReplyEnabled());
        }
        if (request.getProcessEnabled() != null) {
            entity.setProcessEnabled(request.getProcessEnabled());
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
    public EmailSenderRuleResponse toResponse(EmailSenderRule entity) {
        return EmailSenderRuleResponse.builder()
                .id(entity.getId())
                .tenantId(entity.getTenant().getId())
                .tenantCode(entity.getTenant().getTenantCode())
                .emailAccountId(entity.getEmailAccount().getId())
                .emailAccountAddress(entity.getEmailAccount().getEmailAddress())
                .senderEmail(entity.getSenderEmail())
                .senderId(entity.getSenderId())
                .senderName(entity.getSenderName())
                .templateEmailReceived(entity.getTemplateEmailReceived())
                .templateEmailProcessed(entity.getTemplateEmailProcessed())
                .autoReplyEnabled(entity.getAutoReplyEnabled())
                .processEnabled(entity.getProcessEnabled())
                .description(entity.getDescription())
                .enabled(entity.getEnabled())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .attachmentRules(entity.getAttachmentRules().stream()
                        .map(attachmentRuleMapper::toResponse)
                        .collect(Collectors.toList()))
                .build();
    }

    /**
     * Convertir Entity a Response sin incluir attachment rules (para evitar lazy loading)
     */
    public EmailSenderRuleResponse toResponseWithoutRules(EmailSenderRule entity) {
        return EmailSenderRuleResponse.builder()
                .id(entity.getId())
                .tenantId(entity.getTenant().getId())
                .tenantCode(entity.getTenant().getTenantCode())
                .emailAccountId(entity.getEmailAccount().getId())
                .emailAccountAddress(entity.getEmailAccount().getEmailAddress())
                .senderEmail(entity.getSenderEmail())
                .senderId(entity.getSenderId())
                .senderName(entity.getSenderName())
                .templateEmailReceived(entity.getTemplateEmailReceived())
                .templateEmailProcessed(entity.getTemplateEmailProcessed())
                .autoReplyEnabled(entity.getAutoReplyEnabled())
                .processEnabled(entity.getProcessEnabled())
                .description(entity.getDescription())
                .enabled(entity.getEnabled())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
