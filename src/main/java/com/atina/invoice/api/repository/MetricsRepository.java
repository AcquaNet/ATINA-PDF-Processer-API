package com.atina.invoice.api.repository;

import com.atina.invoice.api.model.Metrics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Metrics repository
 * Manages metrics persistence
 */
@Repository
public interface MetricsRepository extends JpaRepository<Metrics, Long> {

    /**
     * Find metric by key
     */
    Optional<Metrics> findByMetricKey(String metricKey);

    /**
     * Check if metric exists
     */
    boolean existsByMetricKey(String metricKey);

    /**
     * Delete metric by key
     */
    void deleteByMetricKey(String metricKey);
}

