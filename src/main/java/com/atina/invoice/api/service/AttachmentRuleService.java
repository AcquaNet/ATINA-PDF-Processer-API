package com.atina.invoice.api.service;

import com.atina.invoice.api.dto.request.CreateAttachmentRuleRequest;
import com.atina.invoice.api.dto.request.UpdateAttachmentRuleRequest;
import com.atina.invoice.api.dto.response.AttachmentProcessingRuleResponse;
import com.atina.invoice.api.mapper.AttachmentRuleMapper;
import com.atina.invoice.api.model.AttachmentProcessingRule;
import com.atina.invoice.api.model.EmailSenderRule;
import com.atina.invoice.api.repository.AttachmentProcessingRuleRepository;
import com.atina.invoice.api.repository.EmailSenderRuleRepository;
import com.atina.invoice.api.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

/**
 * Service para gestión de reglas de attachment
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttachmentRuleService {

    private final AttachmentProcessingRuleRepository attachmentRuleRepository;
    private final EmailSenderRuleRepository senderRuleRepository;
    private final AttachmentRuleMapper attachmentRuleMapper;

    /**
     * Listar reglas de un sender rule
     */
    @Transactional(readOnly = true)
    public List<AttachmentProcessingRuleResponse> getRulesBySenderRule(Long senderRuleId) {
        // Validar que el sender rule existe y pertenece al tenant
        validateSenderRuleBelongsToTenant(senderRuleId);

        return attachmentRuleRepository.findBySenderRuleIdOrderByRuleOrderAsc(senderRuleId).stream()
                .map(attachmentRuleMapper::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Obtener regla por ID
     */
    @Transactional(readOnly = true)
    public AttachmentProcessingRuleResponse getRuleById(Long id) {
        AttachmentProcessingRule rule = findRuleByIdAndTenant(id);
        return attachmentRuleMapper.toResponse(rule);
    }

    /**
     * Crear nueva regla de attachment
     */
    @Transactional
    public AttachmentProcessingRuleResponse createRule(Long senderRuleId, CreateAttachmentRuleRequest request) {

        // ------------------------------------
        // Validar sender rule
        // ------------------------------------

        EmailSenderRule senderRule = findSenderRuleByIdAndTenant(senderRuleId);

        // Validar que no exista una regla con ese orden
        if (attachmentRuleRepository.existsBySenderRuleIdAndRuleOrder(senderRuleId, request.getRuleOrder())) {
            throw new RuntimeException("Rule order already exists: " + request.getRuleOrder());
        }

        // Validar regex
        validateRegex(request.getFileNameRegex());

        // Crear entidad
        AttachmentProcessingRule rule = attachmentRuleMapper.toEntity(request, senderRule);

        // Guardar
        AttachmentProcessingRule saved = attachmentRuleRepository.save(rule);
        log.info("Created attachment rule #{} for sender rule: {}", 
                saved.getRuleOrder(), senderRule.getSenderEmail());

        return attachmentRuleMapper.toResponse(saved);
    }

    /**
     * Actualizar regla de attachment
     */
    @Transactional
    public AttachmentProcessingRuleResponse updateRule(Long id, UpdateAttachmentRuleRequest request) {
        AttachmentProcessingRule rule = findRuleByIdAndTenant(id);

        // Si se actualiza el orden, validar que no exista
        if (request.getRuleOrder() != null && 
            !request.getRuleOrder().equals(rule.getRuleOrder())) {
            if (attachmentRuleRepository.existsBySenderRuleIdAndRuleOrder(
                    rule.getSenderRule().getId(), request.getRuleOrder())) {
                throw new RuntimeException("Rule order already exists: " + request.getRuleOrder());
            }
        }

        // Si se actualiza el regex, validarlo
        if (request.getFileNameRegex() != null) {
            validateRegex(request.getFileNameRegex());
        }

        // Actualizar campos
        attachmentRuleMapper.updateEntity(rule, request);

        // Guardar
        AttachmentProcessingRule saved = attachmentRuleRepository.save(rule);
        log.info("Updated attachment rule: {}", saved.getId());

        return attachmentRuleMapper.toResponse(saved);
    }

    /**
     * Eliminar regla de attachment
     */
    @Transactional
    public void deleteRule(Long id) {
        AttachmentProcessingRule rule = findRuleByIdAndTenant(id);
        attachmentRuleRepository.delete(rule);
        log.info("Deleted attachment rule: {}", rule.getId());
    }

    /**
     * Reordenar regla (cambiar el order)
     */
    @Transactional
    public AttachmentProcessingRuleResponse reorderRule(Long id, Integer newOrder) {
        AttachmentProcessingRule rule = findRuleByIdAndTenant(id);

        if (newOrder.equals(rule.getRuleOrder())) {
            return attachmentRuleMapper.toResponse(rule);
        }

        // Validar que el nuevo orden no exista
        if (attachmentRuleRepository.existsBySenderRuleIdAndRuleOrder(
                rule.getSenderRule().getId(), newOrder)) {
            throw new RuntimeException("Rule order already exists: " + newOrder);
        }

        rule.setRuleOrder(newOrder);
        AttachmentProcessingRule saved = attachmentRuleRepository.save(rule);
        log.info("Reordered attachment rule {} to order {}", id, newOrder);

        return attachmentRuleMapper.toResponse(saved);
    }

    /**
     * Probar regex contra una lista de nombres de archivo
     */
    public Map<String, Object> testRegex(String regex, List<String> filenames) {
        Map<String, Object> result = new HashMap<>();

        try {
            // Validar que el regex es válido
            Pattern pattern = Pattern.compile(regex);
            result.put("valid", true);
            result.put("regex", regex);

            // Probar contra cada filename
            Map<String, Boolean> matches = new HashMap<>();
            for (String filename : filenames) {
                boolean match = pattern.matcher(filename).matches();
                matches.put(filename, match);
            }

            result.put("matches", matches);
            result.put("totalFiles", filenames.size());
            result.put("matchedFiles", matches.values().stream().filter(m -> m).count());

            log.info("Regex test successful: {} matched {} of {} files", 
                    regex, result.get("matchedFiles"), filenames.size());

        } catch (PatternSyntaxException e) {
            result.put("valid", false);
            result.put("regex", regex);
            result.put("error", e.getMessage());
            result.put("errorIndex", e.getIndex());
            log.warn("Invalid regex pattern: {}", regex);
        }

        return result;
    }

    /**
     * Helper: Validar regex
     */
    private void validateRegex(String regex) {
        try {
            Pattern.compile(regex);
        } catch (PatternSyntaxException e) {
            throw new RuntimeException("Invalid regex pattern: " + e.getMessage());
        }
    }

    /**
     * Helper: Validar que sender rule pertenece al tenant
     */
    private void validateSenderRuleBelongsToTenant(Long senderRuleId) {
        findSenderRuleByIdAndTenant(senderRuleId);
    }

    /**
     * Helper: Buscar sender rule y validar tenant
     */
    private EmailSenderRule findSenderRuleByIdAndTenant(Long senderRuleId) {
        Long tenantId = TenantContext.getTenantId();
        EmailSenderRule senderRule = senderRuleRepository.findById(senderRuleId)
                .orElseThrow(() -> new RuntimeException("Sender rule not found: " + senderRuleId));

        if (!senderRule.getTenant().getId().equals(tenantId)) {
            throw new RuntimeException("Sender rule does not belong to current tenant");
        }

        return senderRule;
    }

    /**
     * Helper: Buscar regla por ID y validar que pertenece al tenant actual
     */
    private AttachmentProcessingRule findRuleByIdAndTenant(Long id) {
        AttachmentProcessingRule rule = attachmentRuleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Attachment rule not found: " + id));

        // Validar que pertenece al tenant a través del sender rule
        validateSenderRuleBelongsToTenant(rule.getSenderRule().getId());

        return rule;
    }
}
