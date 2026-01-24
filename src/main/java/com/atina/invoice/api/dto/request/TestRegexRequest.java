package com.atina.invoice.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request para probar un patrón regex contra una lista de nombres de archivos
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestRegexRequest {

    @NotBlank(message = "Regex pattern is required")
    private String regex;

    @NotEmpty(message = "At least one filename is required")
    private List<String> filenames;
}
