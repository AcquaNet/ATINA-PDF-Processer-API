package com.atina.invoice.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Standard API response wrapper")
public class ApiResponse<T> {

    @Schema(description = "Indicates if request was successful", required = true)
    private boolean success;

    @Schema(description = "Correlation ID for tracing")
    private String correlationId;

    @Schema(description = "Response timestamp")
    @Builder.Default
    private Instant timestamp = Instant.now();

    @Schema(description = "Processing duration in milliseconds")
    private Long duration;

    @Schema(description = "Response data (present on success)")
    private T data;

    @Schema(description = "Error details (present on failure)")
    private ErrorResponse error;

    // Factory methods for success
    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .data(data)
                .build();
    }

    public static <T> ApiResponse<T> success(T data, String correlationId, long duration) {
        return ApiResponse.<T>builder()
                .success(true)
                .correlationId(correlationId)
                .duration(duration)
                .data(data)
                .build();
    }

    // Factory methods for error
    public static <T> ApiResponse<T> error(ErrorResponse error) {
        return ApiResponse.<T>builder()
                .success(false)
                .error(error)
                .build();
    }

    public static <T> ApiResponse<T> error(ErrorResponse error, String correlationId) {
        return ApiResponse.<T>builder()
                .success(false)
                .correlationId(correlationId)
                .error(error)
                .build();
    }
}