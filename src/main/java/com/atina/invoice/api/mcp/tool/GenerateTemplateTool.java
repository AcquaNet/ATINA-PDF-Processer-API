package com.atina.invoice.api.mcp.tool;

import com.atina.invoice.api.ai.service.TemplateGeneratorService;
import com.atina.invoice.api.dto.ai.TemplateGenerationRequest;
import com.atina.invoice.api.dto.ai.TemplateGenerationResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.atina.invoice.api.mcp.util.Base64MultipartFile;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

/**
 * MCP Tool: Generate Template from Examples
 *
 * Automatically generates extraction templates from PDF examples using AI
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GenerateTemplateTool implements McpTool {

    private final TemplateGeneratorService templateGeneratorService;
    private final ObjectMapper objectMapper;

    @Override
    public String getName() {
        return "generate_template_from_examples";
    }

    @Override
    public String getDescription() {
        return "Automatically generates an extraction template from 1-2 PDF examples using AI. " +
                "Analyzes the document structure and creates regex patterns to extract fields. " +
                "Perfect for non-technical users who want to configure extraction without writing JSON/regex. " +
                "Provide PDF samples (as base64), a description of the document type, and optionally " +
                "hints about which fields you want to extract.";
    }

    @Override
    public JsonNode getInputSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = objectMapper.createObjectNode();

        // pdf_examples property
        ObjectNode pdfExamplesProp = objectMapper.createObjectNode();
        pdfExamplesProp.put("type", "array");
        pdfExamplesProp.put("description", "Array of 1-2 PDF examples encoded as base64 strings");
        ObjectNode pdfItemsProp = objectMapper.createObjectNode();
        pdfItemsProp.put("type", "string");
        pdfExamplesProp.set("items", pdfItemsProp);
        pdfExamplesProp.put("minItems", 1);
        pdfExamplesProp.put("maxItems", 5);
        properties.set("pdf_examples", pdfExamplesProp);

        // document_description property
        ObjectNode descProp = objectMapper.createObjectNode();
        descProp.put("type", "string");
        descProp.put("description", "Description of the document type (e.g., 'Invoices from ACME Corp supplier')");
        properties.set("document_description", descProp);

        // field_hints property (optional)
        ObjectNode hintsProp = objectMapper.createObjectNode();
        hintsProp.put("type", "array");
        hintsProp.put("description", "Optional list of field names you want to extract (e.g., ['invoice_number', 'date', 'total'])");
        ObjectNode hintsItemsProp = objectMapper.createObjectNode();
        hintsItemsProp.put("type", "string");
        hintsProp.set("items", hintsItemsProp);
        properties.set("field_hints", hintsProp);

        // document_type property (optional)
        ObjectNode typeProp = objectMapper.createObjectNode();
        typeProp.put("type", "string");
        typeProp.put("description", "Document type hint: 'invoice', 'receipt', 'purchase_order', or 'generic'");
        typeProp.putArray("enum")
                .add("invoice")
                .add("receipt")
                .add("purchase_order")
                .add("generic");
        properties.set("document_type", typeProp);

        schema.set("properties", properties);

        // Required fields
        schema.putArray("required")
                .add("pdf_examples")
                .add("document_description");

        return schema;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> arguments) throws Exception {
        log.info("MCP Tool 'generate_template_from_examples' invoked");

        try {
            // Extract arguments
            @SuppressWarnings("unchecked")
            List<String> pdfExamplesBase64 = (List<String>) arguments.get("pdf_examples");
            String documentDescription = (String) arguments.get("document_description");

            @SuppressWarnings("unchecked")
            List<String> fieldHints = (List<String>) arguments.get("field_hints");

            String documentType = (String) arguments.get("document_type");

            // Validate
            if (pdfExamplesBase64 == null || pdfExamplesBase64.isEmpty()) {
                throw new IllegalArgumentException("pdf_examples is required and must have at least 1 PDF");
            }

            if (documentDescription == null || documentDescription.trim().isEmpty()) {
                throw new IllegalArgumentException("document_description is required");
            }

            // Convert base64 PDFs to MultipartFiles
            List<MultipartFile> pdfFiles = new ArrayList<>();
            for (int i = 0; i < pdfExamplesBase64.size(); i++) {
                String base64Pdf = pdfExamplesBase64.get(i);
                byte[] pdfBytes = Base64.getDecoder().decode(base64Pdf);

                MultipartFile file = new Base64MultipartFile(
                        pdfBytes,
                        "sample_" + i,
                        "sample_" + i + ".pdf",
                        "application/pdf"
                );

                pdfFiles.add(file);
            }

            log.info("Processing {} PDF samples", pdfFiles.size());

            // Build request
            TemplateGenerationRequest request = TemplateGenerationRequest.builder()
                    .pdfExamples(pdfFiles)
                    .documentDescription(documentDescription)
                    .fieldHints(fieldHints)
                    .documentType(documentType)
                    .validateTemplate(true)
                    .build();

            // Generate template using AI
            TemplateGenerationResponse response = templateGeneratorService.generateTemplate(request);

            // Build MCP response
            Map<String, Object> mcpResponse = new HashMap<>();
            mcpResponse.put("success", true);
            mcpResponse.put("template", response.getTemplate());
            mcpResponse.put("template_json", response.getTemplateJson());
            mcpResponse.put("confidence", response.getConfidence());
            mcpResponse.put("fields_detected", response.getFieldsDetected());
            mcpResponse.put("suggestions", response.getSuggestions());
            mcpResponse.put("validation", response.getValidation());
            mcpResponse.put("llm_provider", response.getLlmProvider());
            mcpResponse.put("model_used", response.getModelUsed());
            mcpResponse.put("samples_analyzed", response.getSamplesAnalyzed());

            log.info("Template generated successfully: {} fields, confidence: {}",
                    response.getFieldsDetected() != null ? response.getFieldsDetected().size() : 0,
                    response.getConfidence());

            return mcpResponse;

        } catch (Exception e) {
            log.error("Template generation failed", e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());

            return errorResponse;
        }
    }
}
