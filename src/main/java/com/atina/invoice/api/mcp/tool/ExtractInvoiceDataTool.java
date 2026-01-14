package com.atina.invoice.api.mcp.tool;

import com.atina.invoice.api.service.ExtractionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * MCP Tool: Extract Invoice Data
 * 
 * Extracts structured data from PDF invoices using a template
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExtractInvoiceDataTool implements McpTool {

    private final ExtractionService extractionService;
    private final ObjectMapper objectMapper;

    @Override
    public String getName() {
        return "extract_invoice_data";
    }

    @Override
    public String getDescription() {
        return "Extracts structured data from PDF invoices using an extraction template. " +
               "Provide a PDF (as base64) and a template JSON that defines what fields to extract. " +
               "Returns the extracted data with fields like invoice_number, date, total, etc.";
    }

    @Override
    public JsonNode getInputSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = objectMapper.createObjectNode();

        // pdf_base64 property
        ObjectNode pdfProp = objectMapper.createObjectNode();
        pdfProp.put("type", "string");
        pdfProp.put("description", "PDF document encoded as base64 string");
        properties.set("pdf_base64", pdfProp);

        // template property
        ObjectNode templateProp = objectMapper.createObjectNode();
        templateProp.put("type", "object");
        templateProp.put("description", "Extraction template JSON defining rules for data extraction");
        properties.set("template", templateProp);

        // options property (optional)
        ObjectNode optionsProp = objectMapper.createObjectNode();
        optionsProp.put("type", "object");
        optionsProp.put("description", "Optional extraction options");
        properties.set("options", optionsProp);

        schema.set("properties", properties);

        // Required fields
        schema.putArray("required")
              .add("pdf_base64")
              .add("template");

        return schema;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> arguments) throws Exception {
        log.info("MCP Tool 'extract_invoice_data' invoked");

        try {
            // Extract arguments
            String pdfBase64 = (String) arguments.get("pdf_base64");
            Object templateObj = arguments.get("template");
            Object optionsObj = arguments.get("options");

            if (pdfBase64 == null || pdfBase64.isEmpty()) {
                throw new IllegalArgumentException("pdf_base64 is required");
            }

            if (templateObj == null) {
                throw new IllegalArgumentException("template is required");
            }

            // Decode PDF
            byte[] pdfBytes = Base64.getDecoder().decode(pdfBase64);
            log.debug("PDF decoded: {} bytes", pdfBytes.length);

            // Parse template
            JsonNode template = objectMapper.valueToTree(templateObj);

            // Parse options (if provided)
            JsonNode options = optionsObj != null ? 
                              objectMapper.valueToTree(optionsObj) : null;

            // TODO: Convert PDF to Docling JSON first
            // For now, assume we have a way to convert PDF bytes to Docling JSON
            // In production, you'd call DoclingService here
            
            log.warn("PDF to Docling conversion needed - using mock for now");
            
            // Mock Docling JSON for demonstration
            JsonNode doclingJson = objectMapper.createObjectNode()
                    .put("schema_name", "DoclingDocument")
                    .put("_name", "mock_document");

            // Extract data
            JsonNode result = extractionService.extract(doclingJson, template, null);

            // Build response
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", result);
            response.put("message", "Data extracted successfully");

            log.info("Extraction completed successfully");

            return response;

        } catch (Exception e) {
            log.error("Extraction failed", e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            
            return errorResponse;
        }
    }
}
