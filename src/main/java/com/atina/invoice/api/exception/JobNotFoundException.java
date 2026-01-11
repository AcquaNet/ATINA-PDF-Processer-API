package com.atina.invoice.api.exception;

/**
 * Exception thrown when job is not found
 */
public class JobNotFoundException extends RuntimeException {

    public JobNotFoundException(String jobId) {
        super("Job not found: " + jobId);
    }

    public JobNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}