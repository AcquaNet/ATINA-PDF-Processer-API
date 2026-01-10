package com.atina.invoice.api.repository;

import com.atina.invoice.api.model.Job;
import com.atina.invoice.api.model.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface JobRepository extends JpaRepository<Job, String> {

    List<Job> findByStatus(JobStatus status);

    List<Job> findByCorrelationId(String correlationId);

    List<Job> findByCreatedAtBefore(Instant cutoffDate);

    long countByStatus(JobStatus status);
}