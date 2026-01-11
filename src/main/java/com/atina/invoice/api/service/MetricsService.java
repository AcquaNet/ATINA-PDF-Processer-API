package com.atina.invoice.api.service;

import com.atina.invoice.api.model.JobStatus;
import com.atina.invoice.api.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@RequiredArgsConstructor
public class MetricsService {

    private final JobRepository jobRepository;
    
    // In-memory metrics (reset on restart)
    private final AtomicLong totalExtractions = new AtomicLong(0);
    private final AtomicLong successfulExtractions = new AtomicLong(0);
    private final AtomicLong failedExtractions = new AtomicLong(0);
    private final AtomicLong totalDuration = new AtomicLong(0);

    public void recordExtraction(boolean success, long duration) {
        totalExtractions.incrementAndGet();
        
        if (success) {
            successfulExtractions.incrementAndGet();
        } else {
            failedExtractions.incrementAndGet();
        }
        
        totalDuration.addAndGet(duration);
    }

    public Map<String, Object> getMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        
        // Extraction metrics
        Map<String, Object> extractions = new HashMap<>();
        long total = totalExtractions.get();
        long success = successfulExtractions.get();
        long failure = failedExtractions.get();
        
        extractions.put("total", total);
        extractions.put("success", success);
        extractions.put("failure", failure);
        extractions.put("successRate", total > 0 ? (success * 100.0 / total) : 0.0);
        
        metrics.put("extractions", extractions);
        
        // Performance metrics
        Map<String, Object> performance = new HashMap<>();
        long avgDuration = total > 0 ? (totalDuration.get() / total) : 0;
        performance.put("averageDuration", avgDuration);
        
        metrics.put("performance", performance);
        
        // Job metrics from database
        Map<String, Object> jobs = new HashMap<>();
        jobs.put("pending", jobRepository.countByStatus(JobStatus.PENDING));
        jobs.put("processing", jobRepository.countByStatus(JobStatus.PROCESSING));
        jobs.put("completed", jobRepository.countByStatus(JobStatus.COMPLETED));
        jobs.put("failed", jobRepository.countByStatus(JobStatus.FAILED));
        
        metrics.put("jobs", jobs);
        
        return metrics;
    }
}