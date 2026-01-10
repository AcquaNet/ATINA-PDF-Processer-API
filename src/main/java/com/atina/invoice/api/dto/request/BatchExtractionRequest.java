package com.atina.invoice.api.dto.request;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Batch extraction request for multiple documents")
public class BatchExtractionRequest {

    @NotNull(message = "Template is required")
    @Schema(description = "Template to apply to all documents", required = true)
    private JsonNode template;

    @NotEmpty(message = "Documents list cannot be empty")
    @Schema(description = "List of documents to process", required = true)
    private List<DocumentItem> documents;

    @Valid
    @Schema(description = "Extraction options")
    private ExtractionOptions options;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DocumentItem {
        @NotNull
        @Schema(description = "Unique document identifier", required = true)
        private String id;

        @NotNull
        @Schema(description = "Docling JSON for this document", required = true)
        private JsonNode docling;
    }
}