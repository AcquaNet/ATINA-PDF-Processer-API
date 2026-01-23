package com.atina.invoice.api.service;

import com.atina.invoice.api.dto.request.CreateEmailSenderRuleRequest;
import com.atina.invoice.api.dto.request.ImportSenderConfigRequest;
import com.atina.invoice.api.dto.request.UpdateEmailSenderRuleRequest;
import com.atina.invoice.api.dto.response.EmailSenderRuleResponse;
import com.atina.invoice.api.mapper.EmailSenderRuleMapper;
import com.atina.invoice.api.model.AttachmentProcessingRule;
import com.atina.invoice.api.model.EmailAccount;
import com.atina.invoice.api.model.EmailSenderRule;
import com.atina.invoice.api.model.Tenant;
import com.atina.invoice.api.repository.EmailAccountRepository;
import com.atina.invoice.api.repository.EmailSenderRuleRepository;
import com.atina.invoice.api.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service para gestión de reglas de sender
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailSenderRuleService {

    private final EmailSenderRuleRepository senderRuleRepository;
    private final EmailAccountRepository emailAccountRepository;
    private final TenantRepository tenantRepository;
    private final EmailSenderRuleMapper senderRuleMapper;

    /**
     * Listar todas las reglas de todos los tenants (solo para SYSTEM_ADMIN)
     */
    @Transactional(readOnly = true)
    public List<EmailSenderRuleResponse> getAllRules() {
        return senderRuleRepository.findAll().stream()
                .map(senderRuleMapper::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Obtener regla por ID (sin validar tenant - solo para SYSTEM_ADMIN)
     */
    @Transactional(readOnly = true)
    public EmailSenderRuleResponse getRuleById(Long id) {
        EmailSenderRule rule = senderRuleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Sender rule not found: " + id));
        return senderRuleMapper.toResponse(rule);
    }

    /**
     * Crear nueva regla de sender (sin validar tenant - solo para SYSTEM_ADMIN)
     */
    @Transactional
    public EmailSenderRuleResponse createRule(CreateEmailSenderRuleRequest request) {

        // ------------------------------------------------------------
        // Validar que la email account existe
        // ------------------------------------------------------------

        EmailAccount emailAccount = emailAccountRepository.findById(request.getEmailAccountId())
                .orElseThrow(() -> new RuntimeException("Email account not found: " + request.getEmailAccountId()));


        // ----------------------------------------------------------------
        // Validar que no exista una regla para ese sender en esa cuenta
        // ----------------------------------------------------------------

        if (senderRuleRepository.existsByEmailAccountIdAndSenderEmail(
                request.getEmailAccountId(), request.getSenderEmail())) {
            throw new RuntimeException("Sender rule already exists for: " + request.getSenderEmail());
        }

        // ----------------------------------------------------------------
        // Crear entidad
        // ----------------------------------------------------------------

        EmailSenderRule rule = senderRuleMapper.toEntity(request, emailAccount.getTenant(), emailAccount);

        // ----------------------------------------------------------------
        // Guardar
        // ----------------------------------------------------------------

        EmailSenderRule saved = senderRuleRepository.save(rule);
        log.info("Created sender rule for: {} in account: {}", 
                saved.getSenderEmail(), emailAccount.getEmailAddress());

        return senderRuleMapper.toResponse(saved);
    }

    /**
     * Actualizar regla de sender (sin validar tenant - solo para SYSTEM_ADMIN)
     */
    @Transactional
    public EmailSenderRuleResponse updateRule(Long id, UpdateEmailSenderRuleRequest request) {
        EmailSenderRule rule = senderRuleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Sender rule not found: " + id));

        // Si se actualiza el sender email, validar que no exista
        if (request.getSenderEmail() != null && 
            !request.getSenderEmail().equals(rule.getSenderEmail())) {
            if (senderRuleRepository.existsByEmailAccountIdAndSenderEmail(
                    rule.getEmailAccount().getId(), request.getSenderEmail())) {
                throw new RuntimeException("Sender rule already exists for: " + request.getSenderEmail());
            }
        }

        // Actualizar campos
        senderRuleMapper.updateEntity(rule, request);

        // Guardar
        EmailSenderRule saved = senderRuleRepository.save(rule);
        log.info("Updated sender rule: {}", saved.getSenderEmail());

        return senderRuleMapper.toResponse(saved);
    }

    /**
     * Eliminar regla de sender (sin validar tenant - solo para SYSTEM_ADMIN)
     */
    @Transactional
    public void deleteRule(Long id) {
        EmailSenderRule rule = senderRuleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Sender rule not found: " + id));
        senderRuleRepository.delete(rule);
        log.info("Deleted sender rule: {}", rule.getSenderEmail());
    }

    /**
     * Importar configuración JSON (formato Mulesoft) (sin validar tenant - solo para SYSTEM_ADMIN)
     */
    @Transactional
    public EmailSenderRuleResponse importFromJson(Long emailAccountId, ImportSenderConfigRequest config) {
        // Validar email account
        EmailAccount emailAccount = emailAccountRepository.findById(emailAccountId)
                .orElseThrow(() -> new RuntimeException("Email account not found: " + emailAccountId));

        // Obtener tenant del email account
        Tenant tenant = emailAccount.getTenant();

        // Verificar si ya existe una regla para este sender
        boolean exists = senderRuleRepository.existsByEmailAccountIdAndSenderEmail(
                emailAccountId, config.getEmail());

        if (exists) {
            throw new RuntimeException("Sender rule already exists for: " + config.getEmail() + 
                    ". Delete it first or use update.");
        }

        // Crear sender rule
        EmailSenderRule senderRule = EmailSenderRule.builder()
                .tenant(tenant)
                .emailAccount(emailAccount)
                .senderEmail(config.getEmail())
                .senderId(config.getId())
                .templateEmailReceived(config.getTemplates().getEmailReceived())
                .templateEmailProcessed(config.getTemplates().getEmailProcessed())
                .autoReplyEnabled(true)
                .processEnabled(true)
                .enabled(true)
                .build();

        // Agregar attachment rules
        for (ImportSenderConfigRequest.Rule jsonRule : config.getRules()) {
            AttachmentProcessingRule attachmentRule = AttachmentProcessingRule.builder()
                    .senderRule(senderRule)
                    .ruleOrder(jsonRule.getId())
                    .fileNameRegex(jsonRule.getFileRule())
                    .source(jsonRule.getSource())
                    .destination(jsonRule.getDestination())
                    .processingMethod(jsonRule.getMetodo())
                    .enabled(true)
                    .build();

            senderRule.addAttachmentRule(attachmentRule);
        }

        // Guardar
        EmailSenderRule saved = senderRuleRepository.save(senderRule);
        log.info("Imported sender rule from JSON: {} with {} attachment rules", 
                saved.getSenderEmail(), saved.getAttachmentRules().size());

        return senderRuleMapper.toResponse(saved);
    }
}
