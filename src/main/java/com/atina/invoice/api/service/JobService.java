package com.atina.invoice.api.service;

import com.atina.invoice.api.dto.request.ExtractionOptions;
import com.atina.invoice.api.model.Job;
import com.atina.invoice.api.model.JobStatus;
import com.atina.invoice.api.repository.JobRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobService {

    private final JobRepository jobRepository;
    private final ExtractionService extractionService;
    private final ObjectMapper objectMapper;

    @Transactional
    public Job createJob(JsonNode docling, JsonNode template, ExtractionOptions options, String correlationId) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("docling", docling);
            payload.put("template", template);
            payload.put("options", options);
            
            String payloadJson = objectMapper.writeValueAsString(payload);
            
            Job job = Job.builder()
                    .status(JobStatus.PENDING)
                    .correlationId(correlationId)
                    .requestPayload(payloadJson)
                    .progress(0)
                    .build();
            
            job = jobRepository.save(job);
            log.info("Created job {} with correlationId {}", job.getId(), correlationId);
            
            return job;
            
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to create job", e);
        }
    }

    @Async
    public void processJobAsync(String jobId) {
        // Get correlation ID from job for MDC
        Job job = jobRepository.findById(jobId).orElseThrow();
        MDC.put("correlationId", job.getCorrelationId());
        MDC.put("jobId", jobId);
        
        try {
            log.info("Starting async processing of job {}", jobId);
            processJob(jobId);
        } finally {
            MDC.clear();
        }
    }

    @Transactional
    public void processJob(String jobId) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new com.atina.invoice.api.exception.JobNotFoundException("Job not found: " + jobId));
        
        if (job.getStatus() != JobStatus.PENDING) {
            log.warn("Job {} is not in PENDING status, current status: {}", jobId, job.getStatus());
            return;
        }
        
        try {
            // Update status to PROCESSING
            job.setStatus(JobStatus.PROCESSING);
            job.setStartedAt(Instant.now());
            job.setProgress(10);
            jobRepository.save(job);
            
            // Parse request payload
            Map<String, Object> payload = objectMapper.readValue(job.getRequestPayload(), Map.class);
            JsonNode docling = objectMapper.valueToTree(payload.get("docling"));
            JsonNode template = objectMapper.valueToTree(payload.get("template"));
            
            @SuppressWarnings("unchecked")
            Map<String, Object> optionsMap = (Map<String, Object>) payload.get("options");
            ExtractionOptions options = objectMapper.convertValue(optionsMap, ExtractionOptions.class);
            
            job.setProgress(30);
            jobRepository.save(job);
            
            // Perform extraction
            log.info("Executing extraction for job {}", jobId);
            JsonNode result = extractionService.extract(docling, template, options);
            
            job.setProgress(90);
            jobRepository.save(job);
            
            // Save result
            String resultJson = objectMapper.writeValueAsString(result);
            job.setResultPayload(resultJson);
            job.setStatus(JobStatus.COMPLETED);
            job.setCompletedAt(Instant.now());
            job.setProgress(100);
            job.setDurationMs(ChronoUnit.MILLIS.between(job.getStartedAt(), job.getCompletedAt()));
            
            jobRepository.save(job);
            
            log.info("Job {} completed successfully in {}ms", jobId, job.getDurationMs());
            
        } catch (Exception e) {
            log.error("Job {} failed", jobId, e);
            
            job.setStatus(JobStatus.FAILED);
            job.setCompletedAt(Instant.now());
            job.setErrorMessage(e.getMessage());
            
            if (job.getStartedAt() != null) {
                job.setDurationMs(ChronoUnit.MILLIS.between(job.getStartedAt(), job.getCompletedAt()));
            }
            
            jobRepository.save(job);
        }
    }

    public Job getJob(String jobId) {
        return jobRepository.findById(jobId)
                .orElseThrow(() -> new com.atina.invoice.api.exception.JobNotFoundException("Job not found: " + jobId));
    }

    public JsonNode getJobResult(String jobId) {
        Job job = getJob(jobId);
        
        if (job.getStatus() != JobStatus.COMPLETED) {
            throw new IllegalStateException("Job is not completed yet. Current status: " + job.getStatus());
        }
        
        try {
            return objectMapper.readTree(job.getResultPayload());
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse job result", e);
        }
    }

    // Cleanup old jobs
    @Scheduled(cron = "${app.jobs.cleanup-cron}")
    @Transactional
    public void cleanupOldJobs() {
        int retentionDays = 7; // From config
        Instant cutoffDate = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        
        List<Job> oldJobs = jobRepository.findByCreatedAtBefore(cutoffDate);
        
        if (!oldJobs.isEmpty()) {
            jobRepository.deleteAll(oldJobs);
            log.info("Cleaned up {} old jobs", oldJobs.size());
        }
    }
}