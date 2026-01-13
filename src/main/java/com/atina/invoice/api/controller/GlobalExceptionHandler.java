package com.atina.invoice.api.exception;

import com.atina.invoice.api.dto.response.ApiResponse;
import com.atina.invoice.api.dto.response.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

/**
 * Global exception handler
 * Ejemplo de uso con el nuevo ApiResponse
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handle ExtractionException
     * Ejemplo usando ErrorResponse
     */
    @ExceptionHandler(ExtractionException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleExtractionException(
            ExtractionException ex,
            WebRequest request) {

        log.error("Extraction error", ex);

        ErrorResponse error = ErrorResponse.builder()
                .code("EXTRACTION_ERROR")
                .message(ex.getMessage())
                .path(request.getDescription(false).replace("uri=", ""))
                .build();

        // ✅ Funciona - ApiResponse.error(ErrorResponse, correlationId)
        ApiResponse<ErrorResponse> response = ApiResponse.error(error, MDC.get("correlationId"));

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(response);
    }

    /**
     * Handle DoclingException
     * Ejemplo usando String simple
     */
    @ExceptionHandler(DoclingException.class)
    public ResponseEntity<ApiResponse<?>> handleDoclingException(
            DoclingException ex,
            WebRequest request) {

        log.error("Docling error", ex);

        // ✅ Funciona - ApiResponse.error(String, correlationId)
        ApiResponse<?> response = ApiResponse.error(
                "Docling conversion failed: " + ex.getMessage(),
                MDC.get("correlationId")
        );

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(response);
    }

    /**
     * Handle generic exceptions
     * Ejemplo usando ErrorResponse con todos los parámetros
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleGenericException(
            Exception ex,
            WebRequest request) {

        log.error("Unexpected error", ex);

        long duration = calculateDuration(); // Método auxiliar

        ErrorResponse error = ErrorResponse.builder()
                .code("INTERNAL_ERROR")
                .message("An unexpected error occurred: " + ex.getMessage())
                .path(request.getDescription(false).replace("uri=", ""))
                .build();

        // ✅ Funciona - ApiResponse.error(ErrorResponse, correlationId, duration)
        ApiResponse<ErrorResponse> response = ApiResponse.error(
                error,
                MDC.get("correlationId"),
                duration
        );

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(response);
    }

    /**
     * Handle validation errors
     * Ejemplo usando String con duration
     */
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ApiResponse<?>> handleValidationException(
            ValidationException ex,
            WebRequest request) {

        log.error("Validation error", ex);

        long duration = calculateDuration();

        // ✅ Funciona - ApiResponse.error(String, correlationId, duration)
        ApiResponse<?> response = ApiResponse.error(
                "Validation failed: " + ex.getMessage(),
                MDC.get("correlationId"),
                duration
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(response);
    }

    /**
     * Helper method to calculate request duration
     */
    private long calculateDuration() {
        // Implementar según tu lógica
        return 0L;
    }
}
