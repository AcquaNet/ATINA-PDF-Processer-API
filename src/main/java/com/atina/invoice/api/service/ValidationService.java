package com.atina.invoice.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Service for template validation
 * Validates templates before extraction
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ValidationService {

    private final ObjectMapper objectMapper;

    /**
     * Validate template structure and business rules
     */
    public Map<String, Object> validateTemplate(JsonNode template, boolean strictMode) {
        log.info("Validating template, strictMode: {}", strictMode);

        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> issues = new ArrayList<>();

        try {
            // Validate basic structure
            if (!template.has("templateId")) {
                issues.add(createIssue("ERROR", "templateId", "Missing required field: templateId"));
            }

            if (!template.has("blocks")) {
                issues.add(createIssue("ERROR", "blocks", "Missing required field: blocks"));
            }

            // Validate blocks
            if (template.has("blocks") && template.get("blocks").isArray()) {
                validateBlocks(template.get("blocks"), issues, strictMode);
            }

            // Build summary
            Map<String, Object> summary = new HashMap<>();
            summary.put("templateId", template.has("templateId") ? template.get("templateId").asText() : "unknown");
            summary.put("blocksCount", template.has("blocks") ? template.get("blocks").size() : 0);
            summary.put("rulesCount", countRules(template));
            summary.put("ruleTypes", getRuleTypes(template));

            result.put("valid", issues.stream().noneMatch(i -> "ERROR".equals(i.get("level"))));
            result.put("summary", summary);
            result.put("issues", issues);

        } catch (Exception e) {
            log.error("Validation failed", e);
            issues.add(createIssue("ERROR", "general", "Validation failed: " + e.getMessage()));
            result.put("valid", false);
            result.put("issues", issues);
        }

        return result;
    }

    /**
     * Validate blocks array
     */
    private void validateBlocks(JsonNode blocks, List<Map<String, Object>> issues, boolean strictMode) {
        for (int i = 0; i < blocks.size(); i++) {
            JsonNode block = blocks.get(i);
            String blockPath = "blocks[" + i + "]";

            // Validate block has required fields
            if (!block.has("blockId")) {
                issues.add(createIssue("ERROR", blockPath, "Missing blockId"));
            }

            if (!block.has("rules")) {
                issues.add(createIssue("ERROR", blockPath, "Missing rules"));
            }

            // Validate rules
            if (block.has("rules") && block.get("rules").isArray()) {
                validateRules(block.get("rules"), issues, blockPath, strictMode);
            }
        }
    }

    /**
     * Validate rules array
     */
    private void validateRules(JsonNode rules, List<Map<String, Object>> issues, String blockPath, boolean strictMode) {
        Set<String> supportedTypes = new HashSet<>(Arrays.asList(
                "anchor_proximity",
                "region_anchor_proximity",
                "line_regex",
                "global_regex",
                "table_by_headers"
        ));

        for (int i = 0; i < rules.size(); i++) {
            JsonNode rule = rules.get(i);
            String rulePath = blockPath + ".rules[" + i + "]";

            // Validate rule type
            if (!rule.has("type")) {
                issues.add(createIssue("ERROR", rulePath, "Missing rule type"));
            } else {
                String type = rule.get("type").asText();
                if (!supportedTypes.contains(type)) {
                    issues.add(createIssue(strictMode ? "ERROR" : "WARNING",
                            rulePath,
                            "Unsupported rule type: " + type));
                }
            }

            // Validate field
            if (!rule.has("field")) {
                issues.add(createIssue("ERROR", rulePath, "Missing field"));
            }
        }
    }

    /**
     * Count total rules in template
     */
    private int countRules(JsonNode template) {
        int count = 0;
        if (template.has("blocks") && template.get("blocks").isArray()) {
            for (JsonNode block : template.get("blocks")) {
                if (block.has("rules") && block.get("rules").isArray()) {
                    count += block.get("rules").size();
                }
            }
        }
        return count;
    }

    /**
     * Get rule types distribution
     */
    private Map<String, Integer> getRuleTypes(JsonNode template) {
        Map<String, Integer> types = new HashMap<>();
        if (template.has("blocks") && template.get("blocks").isArray()) {
            for (JsonNode block : template.get("blocks")) {
                if (block.has("rules") && block.get("rules").isArray()) {
                    for (JsonNode rule : block.get("rules")) {
                        if (rule.has("type")) {
                            String type = rule.get("type").asText();
                            types.put(type, types.getOrDefault(type, 0) + 1);
                        }
                    }
                }
            }
        }
        return types;
    }

    /**
     * Create validation issue
     */
    private Map<String, Object> createIssue(String level, String path, String message) {
        Map<String, Object> issue = new HashMap<>();
        issue.put("level", level);
        issue.put("path", path);
        issue.put("message", message);
        return issue;
    }
}
