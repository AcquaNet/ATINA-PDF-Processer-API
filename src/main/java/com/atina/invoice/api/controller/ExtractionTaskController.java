package com.atina.invoice.api.controller;

import com.atina.invoice.api.service.PdfExtractionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/extraction-tasks")
@RequiredArgsConstructor
public class ExtractionTaskController {

    private final PdfExtractionService pdfExtractionService;

    @PostMapping("/{taskId}/retry")
    public ResponseEntity<Map<String, String>> retryTask(@PathVariable Long taskId) {
        try {
            pdfExtractionService.retryExtraction(taskId);

            Map<String, String> response = new HashMap<>();
            response.put("message", "Extraction task queued for retry");
            response.put("task_id", taskId.toString());

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.error("Task not found {}: {}", taskId, e.getMessage());

            Map<String, String> response = new HashMap<>();
            response.put("error", e.getMessage());

            return ResponseEntity.notFound().build();

        } catch (IllegalStateException e) {
            log.error("Cannot retry task {}: {}", taskId, e.getMessage());

            Map<String, String> response = new HashMap<>();
            response.put("error", e.getMessage());

            return ResponseEntity.badRequest().body(response);
        }
    }
}
