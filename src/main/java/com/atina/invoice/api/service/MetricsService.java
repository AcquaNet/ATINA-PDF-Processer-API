package com.atina.invoice.api.service;

import com.atina.invoice.api.model.Job;
import com.atina.invoice.api.model.JobStatus;
import com.atina.invoice.api.model.Metrics;
import com.atina.invoice.api.repository.JobRepository;
import com.atina.invoice.api.repository.MetricsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Metrics service with database persistence
 * Tracks and stores application metrics in database
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetricsService {

    private final MetricsRepository metricsRepository;
    private final JobRepository jobRepository;

    // Metric keys
    private static final String EXTRACTIONS_TOTAL = "extractions.total";
    private static final String EXTRACTIONS_SUCCESS = "extractions.success";
    private static final String EXTRACTIONS_FAILURE = "extractions.failure";
    private static final String DOCLING_CONVERSIONS = "docling.conversions";
    private static final String DOCLING_FAILURES = "docling.failures";

    /**
     * Increment a metric
     */
    @Transactional
    public void incrementMetric(String metricKey) {
        incrementMetric(metricKey, 1L);
    }

    /**
     * Increment a metric by a specific amount
     */
    @Transactional
    public void incrementMetric(String metricKey, long amount) {
        Metrics metric = metricsRepository.findByMetricKey(metricKey)
                .orElseGet(() -> {
                    log.debug("Creating new metric: {}", metricKey);
                    return Metrics.builder()
                            .metricKey(metricKey)
                            .metricValue(0L)
                            .createdAt(Instant.now())
                            .updatedAt(Instant.now())
                            .build();
                });

        metric.setMetricValue(metric.getMetricValue() + amount);
        metric.setUpdatedAt(Instant.now());
        metricsRepository.save(metric);

        log.debug("Incremented metric {} by {} to {}", metricKey, amount, metric.getMetricValue());
    }

    /**
     * Get a metric value
     */
    public long getMetric(String metricKey) {
        return metricsRepository.findByMetricKey(metricKey)
                .map(Metrics::getMetricValue)
                .orElse(0L);
    }

    /**
     * Set a metric to a specific value
     */
    @Transactional
    public void setMetric(String metricKey, long value) {
        Metrics metric = metricsRepository.findByMetricKey(metricKey)
                .orElseGet(() -> {
                    log.debug("Creating new metric: {}", metricKey);
                    return Metrics.builder()
                            .metricKey(metricKey)
                            .metricValue(0L)
                            .createdAt(Instant.now())
                            .updatedAt(Instant.now())
                            .build();
                });

        metric.setMetricValue(value);
        metric.setUpdatedAt(Instant.now());
        metricsRepository.save(metric);

        log.debug("Set metric {} to {}", metricKey, value);
    }

    /**
     * Record successful extraction
     */
    public void recordExtractionSuccess() {
        incrementMetric(EXTRACTIONS_TOTAL);
        incrementMetric(EXTRACTIONS_SUCCESS);
        log.debug("Recorded extraction success");
    }

    /**
     * Record failed extraction
     */
    public void recordExtractionFailure() {
        incrementMetric(EXTRACTIONS_TOTAL);
        incrementMetric(EXTRACTIONS_FAILURE);
        log.debug("Recorded extraction failure");
    }

    /**
     * Record Docling conversion
     */
    public void recordDoclingConversion() {
        incrementMetric(DOCLING_CONVERSIONS);
        log.debug("Recorded Docling conversion");
    }

    /**
     * Record Docling failure
     */
    public void recordDoclingFailure() {
        incrementMetric(DOCLING_FAILURES);
        log.debug("Recorded Docling failure");
    }

    /**
     * Get all metrics
     */
    public Map<String, Object> getMetrics() {
        // Get extraction metrics from DB
        long extractionsTotal = getMetric(EXTRACTIONS_TOTAL);
        long extractionsSuccess = getMetric(EXTRACTIONS_SUCCESS);
        long extractionsFailure = getMetric(EXTRACTIONS_FAILURE);

        // Calculate success rate
        double successRate = extractionsTotal > 0
                ? (extractionsSuccess * 100.0) / extractionsTotal
                : 0.0;

        // Get job statistics from DB
        Map<String, Object> jobStats = getJobStatistics();

        // Get Docling metrics
        long doclingConversions = getMetric(DOCLING_CONVERSIONS);
        long doclingFailures = getMetric(DOCLING_FAILURES);

        // Build response
        Map<String, Object> metrics = new HashMap<>();

        // Extraction metrics
        Map<String, Object> extractions = new HashMap<>();
        extractions.put("total", extractionsTotal);
        extractions.put("success", extractionsSuccess);
        extractions.put("failure", extractionsFailure);
        extractions.put("successRate", Math.round(successRate * 100.0) / 100.0);
        metrics.put("extractions", extractions);

        // Docling metrics
        Map<String, Object> docling = new HashMap<>();
        docling.put("conversions", doclingConversions);
        docling.put("failures", doclingFailures);
        metrics.put("docling", docling);

        // Job metrics
        metrics.put("jobs", jobStats);

        // Performance metrics
        Map<String, Object> performance = new HashMap<>();
        performance.put("averageExtractionDuration", calculateAverageExtractionDuration());
        metrics.put("performance", performance);

        log.debug("Retrieved metrics: {}", metrics);
        return metrics;
    }

    /**
     * Get job statistics from database
     */
    private Map<String, Object> getJobStatistics() {
        List<Job> allJobs = jobRepository.findAll();

        long pending = allJobs.stream()
                .filter(j -> j.getStatus() == JobStatus.PENDING)
                .count();

        long processing = allJobs.stream()
                .filter(j -> j.getStatus() == JobStatus.PROCESSING)
                .count();

        long completed = allJobs.stream()
                .filter(j -> j.getStatus() == JobStatus.COMPLETED)
                .count();

        long failed = allJobs.stream()
                .filter(j -> j.getStatus() == JobStatus.FAILED)
                .count();

        long cancelled = allJobs.stream()
                .filter(j -> j.getStatus() == JobStatus.CANCELLED)
                .count();

        Map<String, Object> jobStats = new HashMap<>();
        jobStats.put("total", allJobs.size());
        jobStats.put("pending", pending);
        jobStats.put("processing", processing);
        jobStats.put("completed", completed);
        jobStats.put("failed", failed);
        jobStats.put("cancelled", cancelled);

        return jobStats;
    }

    /**
     * Calculate average extraction duration
     */
    private long calculateAverageExtractionDuration() {
        List<Job> completedJobs = jobRepository.findAll().stream()
                .filter(j -> j.getStatus() == JobStatus.COMPLETED)
                .filter(j -> j.getDurationMs() != null)
                .toList();

        if (completedJobs.isEmpty()) {
            return 0L;
        }

        long totalDuration = completedJobs.stream()
                .mapToLong(Job::getDurationMs)
                .sum();

        return totalDuration / completedJobs.size();
    }

    /**
     * Reset all metrics
     */
    @Transactional
    public void resetMetrics() {
        log.info("Resetting all metrics");

        // Delete all metrics from database
        metricsRepository.deleteAll();

        log.info("All metrics reset to zero");
    }

    /**
     * Reset specific metric
     */
    @Transactional
    public void resetMetric(String metricKey) {
        log.info("Resetting metric: {}", metricKey);
        metricsRepository.deleteByMetricKey(metricKey);
        log.info("Metric {} reset to zero", metricKey);
    }

    /**
     * Get all stored metrics (for debugging)
     */
    public List<Metrics> getAllStoredMetrics() {
        return metricsRepository.findAll();
    }
}
