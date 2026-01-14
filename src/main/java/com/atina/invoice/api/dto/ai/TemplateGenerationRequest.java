package com.atina.invoice.api.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Template Generation Request DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateGenerationRequest {

    /**
     * PDF examples (1-2 samples recommended)
     * Base64 strings or MultipartFiles
     */
    private List<String> pdfExamplesBase64;
    
    /**
     * Alternative: MultipartFiles (for API endpoints)
     */
    private List<MultipartFile> pdfExamples;

    /**
     * Document description
     * Example: "Invoices from ACME Corp supplier"
     */
    private String documentDescription;

    /**
     * Desired fields to extract (hints)
     * Example: ["invoice_number", "date", "total", "customer_name"]
     */
    private List<String> fieldHints;

    /**
     * Document type hint (optional)
     * Values: invoice, receipt, purchase_order, generic
     */
    private String documentType;

    /**
     * Whether to validate generated template (default: true)
     */
    @Builder.Default
    private Boolean validateTemplate = true;

    /**
     * LLM provider to use (optional, overrides config)
     * Values: openai, anthropic, ollama
     */
    private String llmProvider;
}
