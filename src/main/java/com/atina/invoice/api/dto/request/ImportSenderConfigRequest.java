package com.atina.invoice.api.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO para importar configuración JSON existente
 * Compatible con el formato de Mulesoft
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportSenderConfigRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;

    @NotBlank(message = "ID is required")
    private String id;

    @NotNull(message = "Templates configuration is required")
    private Templates templates;

    @NotEmpty(message = "At least one rule is required")
    private List<Rule> rules;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Templates {
        @JsonProperty("email-received")
        private String emailReceived;

        @JsonProperty("email-processed")
        private String emailProcessed;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Rule {
        private Integer id;

        @NotBlank(message = "fileRule is required")
        private String fileRule;

        @NotBlank(message = "source is required")
        private String source;

        @NotBlank(message = "destination is required")
        private String destination;

        private String metodo; // processingMethod
    }
}
