package com.atina.invoice.api.dto.response;

import com.atina.invoice.api.model.JobStatus;
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
@Schema(description = "Async job response")
public class JobResponse {

    @Schema(description = "Job ID", required = true)
    private String jobId;

    @Schema(description = "Job status", required = true)
    private JobStatus status;

    @Schema(description = "Progress percentage (0-100)")
    private Integer progress;

    @Schema(description = "Job creation timestamp")
    private Instant createdAt;

    @Schema(description = "Job start timestamp")
    private Instant startedAt;

    @Schema(description = "Job completion timestamp")
    private Instant completedAt;

    @Schema(description = "Estimated completion time")
    private Instant estimatedCompletion;

    @Schema(description = "Processing duration in milliseconds")
    private Long duration;

    @Schema(description = "URL to check job status")
    private String statusUrl;

    @Schema(description = "URL to get job result (when completed)")
    private String resultUrl;

    @Schema(description = "Job result data (only when status is COMPLETED)")
    private Object result;

    @Schema(description = "Error message (only when status is FAILED)")
    private String errorMessage;
}