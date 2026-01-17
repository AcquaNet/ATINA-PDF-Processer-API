package com.atina.invoice.api.dto.request;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

/**
 * Request para extracción (DEPRECADO)
 *
 * NOTA: Este DTO ya no se usa directamente en el controller.
 * El ExtractionController ahora recibe parámetros individuales con @RequestPart.
 *
 * Se mantiene por compatibilidad con código legacy.
 *
 * @deprecated El controller ahora usa @RequestPart individuales en lugar de @ModelAttribute
 */
@Data
@Deprecated
public class ExtractionRequest {

    /**
     * PDF como archivo
     */
    private MultipartFile pdfFile;

    /**
     * Path al PDF en filesystem
     */
    private String pdfPath;

    /**
     * Template como archivo
     */
    private MultipartFile templateFile;

    /**
     * Path al template en filesystem
     */
    private String templatePath;

    /**
     * Template como JSON (texto)
     */
    private String template;

    /**
     * Opciones de extracción como JSON (texto)
     */
    private String options;
}
