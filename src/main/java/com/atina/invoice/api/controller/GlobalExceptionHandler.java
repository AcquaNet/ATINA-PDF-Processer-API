package com.atina.invoice.api.controller;

import com.atina.invoice.api.dto.response.ApiResponse;
import com.atina.invoice.api.dto.response.ErrorResponse;
import com.atina.invoice.api.exception.ValidationException;
import com.atina.invoice.api.exception.ExtractionException;
import com.atina.invoice.api.exception.JobNotFoundException;
import com.atina.invoice.api.exception.DoclingException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Global exception handler for REST API
 * Handles all exceptions and returns consistent error responses
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handle validation exceptions (Bean Validation)
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<?>> handleValidationException(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {
        
        log.warn("Validation error: {}", ex.getMessage());
        
        List<String> details = ex.getBindingResult()
            .getFieldErrors()
            .stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .collect(Collectors.toList());
        
        ErrorResponse error = ErrorResponse.builder()
            .code("VALIDATION_ERROR")
            .message("Validation failed")
            .details(details)
            .path(request.getRequestURI())
            .build();
        
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.error(error, MDC.get("correlationId")));
    }

    /**
     * Handle validation exception (custom)
     */
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ApiResponse<?>> handleValidationException(
            ValidationException ex,
            HttpServletRequest request) {
        
        log.warn("Validation exception: {}", ex.getMessage());
        
        ErrorResponse error = ErrorResponse.builder()
            .code("VALIDATION_ERROR")
            .message(ex.getMessage())
            .path(request.getRequestURI())
            .build();
        
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.error(error, MDC.get("correlationId")));
    }

    /**
     * Handle extraction exception
     */
    @ExceptionHandler(ExtractionException.class)
    public ResponseEntity<ApiResponse<?>> handleExtractionException(
            ExtractionException ex,
            HttpServletRequest request) {
        
        log.error("Extraction exception: {}", ex.getMessage());
        
        ErrorResponse error = ErrorResponse.builder()
            .code("EXTRACTION_ERROR")
            .message("Extraction failed: " + ex.getMessage())
            .path(request.getRequestURI())
            .build();
        
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.error(error, MDC.get("correlationId")));
    }

    /**
     * Handle job not found exception
     */
    @ExceptionHandler(JobNotFoundException.class)
    public ResponseEntity<ApiResponse<?>> handleJobNotFoundException(
            JobNotFoundException ex,
            HttpServletRequest request) {
        
        log.warn("Job not found: {}", ex.getMessage());
        
        ErrorResponse error = ErrorResponse.builder()
            .code("JOB_NOT_FOUND")
            .message(ex.getMessage())
            .path(request.getRequestURI())
            .build();
        
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ApiResponse.error(error, MDC.get("correlationId")));
    }

    /**
     * Handle Docling exception
     */
    @ExceptionHandler(DoclingException.class)
    public ResponseEntity<ApiResponse<?>> handleDoclingException(
            DoclingException ex,
            HttpServletRequest request) {
        
        log.error("Docling exception: {}", ex.getMessage());
        
        ErrorResponse error = ErrorResponse.builder()
            .code("DOCLING_ERROR")
            .message("PDF conversion failed: " + ex.getMessage())
            .path(request.getRequestURI())
            .build();
        
        return ResponseEntity
            .status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(ApiResponse.error(error, MDC.get("correlationId")));
    }

    /**
     * Handle authentication exceptions
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<?>> handleBadCredentialsException(
            BadCredentialsException ex,
            HttpServletRequest request) {
        
        log.warn("Bad credentials: {}", ex.getMessage());
        
        ErrorResponse error = ErrorResponse.builder()
            .code("UNAUTHORIZED")
            .message(ex.getMessage())
            .path(request.getRequestURI())
            .build();
        
        return ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .body(ApiResponse.error(error, MDC.get("correlationId")));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<?>> handleAuthenticationException(
            AuthenticationException ex,
            HttpServletRequest request) {
        
        log.warn("Authentication failed: {}", ex.getMessage());
        
        ErrorResponse error = ErrorResponse.builder()
            .code("UNAUTHORIZED")
            .message("Authentication failed")
            .path(request.getRequestURI())
            .build();
        
        return ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .body(ApiResponse.error(error, MDC.get("correlationId")));
    }

    /**
     * Handle method argument type mismatch
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<?>> handleTypeMismatchException(
            MethodArgumentTypeMismatchException ex,
            HttpServletRequest request) {
        
        log.warn("Type mismatch: {}", ex.getMessage());
        
        String message = String.format(
            "Parameter '%s' should be of type %s",
            ex.getName(),
            ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown"
        );
        
        ErrorResponse error = ErrorResponse.builder()
            .code("INVALID_PARAMETER")
            .message(message)
            .path(request.getRequestURI())
            .build();
        
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.error(error, MDC.get("correlationId")));
    }

    /**
     * Handle max upload size exceeded
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<?>> handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException ex,
            HttpServletRequest request) {
        
        log.warn("File too large: {}", ex.getMessage());
        
        ErrorResponse error = ErrorResponse.builder()
            .code("FILE_TOO_LARGE")
            .message("File size exceeds maximum allowed size")
            .path(request.getRequestURI())
            .build();
        
        return ResponseEntity
            .status(HttpStatus.PAYLOAD_TOO_LARGE)
            .body(ApiResponse.error(error, MDC.get("correlationId")));
    }

    /**
     * Handle illegal argument exception
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<?>> handleIllegalArgumentException(
            IllegalArgumentException ex,
            HttpServletRequest request) {
        
        log.warn("Illegal argument: {}", ex.getMessage());
        
        ErrorResponse error = ErrorResponse.builder()
            .code("INVALID_ARGUMENT")
            .message(ex.getMessage())
            .path(request.getRequestURI())
            .build();
        
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.error(error, MDC.get("correlationId")));
    }

    /**
     * Handle illegal state exception
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<?>> handleIllegalStateException(
            IllegalStateException ex,
            HttpServletRequest request) {
        
        log.error("Illegal state: {}", ex.getMessage());
        
        ErrorResponse error = ErrorResponse.builder()
            .code("ILLEGAL_STATE")
            .message(ex.getMessage())
            .path(request.getRequestURI())
            .build();
        
        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(ApiResponse.error(error, MDC.get("correlationId")));
    }

    /**
     * Handle generic runtime exception
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<?>> handleRuntimeException(
            RuntimeException ex,
            HttpServletRequest request) {
        
        log.error("Runtime exception: {}", ex.getMessage(), ex);
        
        ErrorResponse error = ErrorResponse.builder()
            .code("RUNTIME_ERROR")
            .message("An unexpected error occurred: " + ex.getMessage())
            .path(request.getRequestURI())
            .build();
        
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.error(error, MDC.get("correlationId")));
    }

    /**
     * Handle generic exception (catch-all)
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<?>> handleException(
            Exception ex,
            HttpServletRequest request) {
        
        log.error("Unexpected exception: {}", ex.getMessage(), ex);
        
        ErrorResponse error = ErrorResponse.builder()
            .code("INTERNAL_ERROR")
            .message("An internal server error occurred")
            .path(request.getRequestURI())
            .build();
        
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.error(error, MDC.get("correlationId")));
    }
}
