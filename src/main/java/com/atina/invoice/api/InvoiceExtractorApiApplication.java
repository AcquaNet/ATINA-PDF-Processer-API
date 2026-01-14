package com.atina.invoice.api;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Invoice Extractor API - Main Application
 * 
 * REST API for extracting structured invoice data using AI-powered rules engine.
 * 
 * Features:
 * - Synchronous and asynchronous extraction
 * - Batch processing
 * - Template validation
 * - JWT authentication
 * - Swagger documentation
 * - Health checks and metrics
 * 
 * @author Atina
 * @version 1.0.0
 */
@Slf4j
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class InvoiceExtractorApiApplication {

    public static void main(String[] args) {


        String key = System.getenv("OPENAI_API_KEY");
        if (key != null) {
            log.info("OPENAI_API_KEY found");
        } else {
            log.error("OPENAI_API_KEY Not found");
        }
        System.out.println("================================");


        SpringApplication.run(InvoiceExtractorApiApplication.class, args);
    }
}
