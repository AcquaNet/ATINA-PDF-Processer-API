package com.atina.invoice.api.service;

import com.atina.invoicetrainer.engine.TemplateLoader;
import com.atina.invoicetrainer.engine.TemplateModel.Template;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ValidationService {

    private final ObjectMapper objectMapper;
    private Process log;

    public ValidationService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> validateTemplate(JsonNode templateNode, boolean strictMode) {
        log.info("Validating template (strictMode: {})", strictMode);
        
        Map<String, Object> result = new HashMap<>();
        List<Map<String, String>> validations = new ArrayList<>();
        
        try {
            String templateJson = objectMapper.writeValueAsString(templateNode);
            
            // Load template using TemplateLoader
            Template template = TemplateLoader.loadFromJson(templateJson);
            
            // Basic validation passed if we got here
            result.put("valid", true);
            
            // Extract template info
            Map<String, Object> templateInfo = new HashMap<>();
            templateInfo.put("templateId", template.templateId());
            templateInfo.put("blocksCount", template.blocks() != null ? template.blocks().size() : 0);
            templateInfo.put("rulesCount", template.rules() != null ? template.rules().size() : 0);
            
            // Count rule types
            Map<String, Integer> ruleTypes = new HashMap<>();
            if (template.rules() != null) {
                template.rules().forEach(rule -> {
                    String type = rule.type();
                    ruleTypes.put(type, ruleTypes.getOrDefault(type, 0) + 1);
                });
            }
            templateInfo.put("ruleTypes", ruleTypes);
            
            result.put("template", templateInfo);
            
            // Additional validations in strict mode
            if (strictMode) {
                // Check for common issues
                if (template.rules() == null || template.rules().isEmpty()) {
                    Map<String, String> issue = new HashMap<>();
                    issue.put("severity", "WARNING");
                    issue.put("path", "rules");
                    issue.put("message", "No rules defined in template");
                    validations.add(issue);
                }
                
                // Check for duplicate rule IDs
                if (template.rules() != null) {
                    Map<String, Long> idCounts = new HashMap<>();
                    template.rules().forEach(rule -> {
                        String id = rule.id();
                        idCounts.put(id, idCounts.getOrDefault(id, 0L) + 1);
                    });
                    
                    idCounts.forEach((id, count) -> {
                        if (count > 1) {
                            Map<String, String> issue = new HashMap<>();
                            issue.put("severity", "ERROR");
                            issue.put("path", "rules");
                            issue.put("message", "Duplicate rule ID: " + id);
                            validations.add(issue);
                            result.put("valid", false);
                        }
                    });
                }
            }
            
            result.put("validations", validations);
            
            log.info("Template validation completed. Valid: {}, Issues: {}", 
                result.get("valid"), validations.size());
            
        } catch (Exception e) {
            log.error("Template validation failed", e);
            
            result.put("valid", false);
            
            Map<String, String> issue = new HashMap<>();
            issue.put("severity", "ERROR");
            issue.put("path", "template");
            issue.put("message", "Template parsing failed: " + e.getMessage());
            validations.add(issue);
            
            result.put("validations", validations);
        }
        
        return result;
    }
}