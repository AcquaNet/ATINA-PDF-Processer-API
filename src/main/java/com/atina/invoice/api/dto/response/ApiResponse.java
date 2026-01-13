package com.atina.invoice.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Generic API response wrapper
 * Wraps all API responses with consistent structure
 * Supports both String error messages and ErrorResponse objects
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    /**
     * Indicates if the request was successful
     */
    private boolean success;

    /**
     * Response data (only present on success)
     */
    private T data;

    /**
     * Error message (only present on failure)
     */
    private String error;

    /**
     * Correlation ID for request tracking
     */
    private String correlationId;

    /**
     * Timestamp of the response
     */
    private Instant timestamp;

    /**
     * Request duration in milliseconds
     */
    private Long duration;

    // ============================================
    // SUCCESS METHODS
    // ============================================

    /**
     * Create successful response
     */
    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .data(data)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Create successful response with correlation ID
     */
    public static <T> ApiResponse<T> success(T data, String correlationId) {
        return ApiResponse.<T>builder()
                .success(true)
                .data(data)
                .correlationId(correlationId)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Create successful response with correlation ID and duration
     */
    public static <T> ApiResponse<T> success(T data, String correlationId, Long duration) {
        return ApiResponse.<T>builder()
                .success(true)
                .data(data)
                .correlationId(correlationId)
                .duration(duration)
                .timestamp(Instant.now())
                .build();
    }

    // ============================================
    // ERROR METHODS - STRING
    // ============================================

    /**
     * Create error response with String message
     */
    public static <T> ApiResponse<T> error(String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .error(message)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Create error response with String message and correlation ID
     */
    public static <T> ApiResponse<T> error(String message, String correlationId) {
        return ApiResponse.<T>builder()
                .success(false)
                .error(message)
                .correlationId(correlationId)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Create error response with String message, correlation ID and duration
     */
    public static <T> ApiResponse<T> error(String message, String correlationId, Long duration) {
        return ApiResponse.<T>builder()
                .success(false)
                .error(message)
                .correlationId(correlationId)
                .duration(duration)
                .timestamp(Instant.now())
                .build();
    }

    // ============================================
    // ERROR METHODS - ErrorResponse OBJECT
    // ============================================

    /**
     * Create error response with ErrorResponse object
     * ErrorResponse details are included in 'data' field
     */
    public static ApiResponse<ErrorResponse> error(ErrorResponse errorResponse) {
        return ApiResponse.<ErrorResponse>builder()
                .success(false)
                .error(errorResponse.getMessage())  // Message in 'error' field
                .data(errorResponse)                 // Full details in 'data' field
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Create error response with ErrorResponse object and correlation ID
     */
    public static ApiResponse<ErrorResponse> error(ErrorResponse errorResponse, String correlationId) {
        return ApiResponse.<ErrorResponse>builder()
                .success(false)
                .error(errorResponse.getMessage())
                .data(errorResponse)
                .correlationId(correlationId)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Create error response with ErrorResponse object, correlation ID and duration
     */
    public static ApiResponse<ErrorResponse> error(ErrorResponse errorResponse, String correlationId, Long duration) {
        return ApiResponse.<ErrorResponse>builder()
                .success(false)
                .error(errorResponse.getMessage())
                .data(errorResponse)
                .correlationId(correlationId)
                .duration(duration)
                .timestamp(Instant.now())
                .build();
    }
}
