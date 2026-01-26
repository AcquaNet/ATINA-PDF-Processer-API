package com.atina.invoice.api.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for PDF extraction
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "extraction")
public class ExtractionProperties {

    /**
     * Worker configuration
     */
    private Worker worker = new Worker();

    /**
     * Webhook configuration
     */
    private Webhook webhook = new Webhook();

    @Data
    public static class Worker {
        /**
         * Enable/disable extraction worker
         */
        private boolean enabled = true;

        /**
         * Poll interval in milliseconds (how often to check for new tasks)
         */
        private long pollIntervalMs = 5000; // 5 seconds

        /**
         * Batch size (max tasks to process per cycle)
         */
        private int batchSize = 5;

        /**
         * Base retry delay in seconds
         */
        private int retryDelaySeconds = 60;

        /**
         * Stuck task threshold in minutes
         * Tasks in PROCESSING state longer than this are considered stuck
         */
        private int stuckTaskThresholdMinutes = 30;
    }

    @Data
    public static class Webhook {
        /**
         * Enable/disable webhooks
         */
        private boolean enabled = true;

        /**
         * Webhook timeout in seconds
         */
        private int timeoutSeconds = 30;

        /**
         * Number of retry attempts for failed webhooks
         */
        private int retryAttempts = 3;

        /**
         * Base retry delay in seconds (uses exponential backoff)
         */
        private int retryDelaySeconds = 60;
    }
}
