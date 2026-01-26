package com.atina.invoice.api.service;

import com.atina.invoice.api.model.ExtractionTask;
import com.atina.invoice.api.model.ProcessedAttachment;
import com.atina.invoice.api.model.ProcessedEmail;
import com.atina.invoice.api.model.enums.AttachmentProcessingStatus;
import com.atina.invoice.api.model.enums.ExtractionStatus;
import com.atina.invoice.api.repository.ExtractionTaskRepository;
import com.atina.invoice.api.repository.ProcessedAttachmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Servicio para gestionar la extracción asíncrona de PDFs
 *
 * Este servicio es llamado por EmailPollingService después de guardar los PDFs
 * para crear tareas de extracción que serán procesadas por ExtractionWorker
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PdfExtractionService {

    private final ExtractionTaskRepository taskRepository;
    private final ProcessedAttachmentRepository attachmentRepository;

    /**
     * Encolar attachments de un email para extracción asíncrona
     *
     * Este método se llama desde EmailPollingService después de:
     * - Guardar PDFs en filesystem
     * - Crear ProcessedEmail y ProcessedAttachments
     *
     * @param email Email procesado con attachments
     * @return Lista de tareas creadas
     */
    @Transactional
    public List<ExtractionTask> enqueueEmailExtractions(ProcessedEmail email) {
        log.info("📋 Enqueuing extractions for email: {} (total attachments: {})",
                email.getId(), email.getTotalAttachments());

        // 1. Obtener attachments DOWNLOADED (descargados exitosamente)
        List<ProcessedAttachment> attachments = attachmentRepository
                .findByProcessedEmailIdAndProcessingStatus(
                        email.getId(),
                        AttachmentProcessingStatus.DOWNLOADED
                );

        if (attachments.isEmpty()) {
            log.info("No attachments to extract for email: {}", email.getId());
            return new ArrayList<>();
        }

        log.info("Found {} attachments ready for extraction", attachments.size());

        // 2. Crear tarea por cada attachment
        List<ExtractionTask> tasks = attachments.stream()
                .map(att -> createTask(email, att))
                .collect(Collectors.toList());

        log.info("✅ Created {} extraction tasks for email: {}", tasks.size(), email.getId());

        return tasks;
    }

    /**
     * Crear tarea de extracción para un attachment
     */
    private ExtractionTask createTask(ProcessedEmail email, ProcessedAttachment attachment) {
        // Obtener source de la regla del attachment
        String source = attachment.getRule() != null
                ? attachment.getRule().getSource()
                : "unknown";

        ExtractionTask task = ExtractionTask.builder()
                .email(email)
                .attachment(attachment)
                .pdfPath(attachment.getFilePath())
                .source(source)
                .status(ExtractionStatus.PENDING)
                .priority(0) // Default priority
                .attempts(0)
                .maxAttempts(3)
                .build();

        ExtractionTask savedTask = taskRepository.save(task);

        log.debug("Created extraction task {} for attachment {} (source: {}, path: {})",
                savedTask.getId(),
                attachment.getId(),
                source,
                attachment.getFilePath());

        return savedTask;
    }

    /**
     * Reintentar una tarea fallida
     *
     * @param taskId ID de la tarea
     */
    @Transactional
    public void retryExtraction(Long taskId) {
        ExtractionTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));

        if (task.getStatus() != ExtractionStatus.FAILED) {
            throw new IllegalStateException(
                    "Task " + taskId + " is not in FAILED state, cannot retry"
            );
        }

        log.info("Retrying extraction task: {}", taskId);

        task.setStatus(ExtractionStatus.PENDING);
        task.setAttempts(0);
        task.setErrorMessage(null);
        task.setNextRetryAt(null);

        taskRepository.save(task);
    }

    /**
     * Cancelar una tarea
     *
     * @param taskId ID de la tarea
     */
    @Transactional
    public void cancelTask(Long taskId) {
        ExtractionTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));

        if (task.isTerminal()) {
            log.warn("Task {} is already in terminal state: {}", taskId, task.getStatus());
            return;
        }

        log.info("Cancelling extraction task: {}", taskId);

        task.cancel();
        taskRepository.save(task);
    }

    /**
     * Obtener estadísticas de extracción de un email
     *
     * @param emailId ID del email
     * @return Mapa con estadísticas
     */
    public Map<String, Object> getEmailExtractionStats(Long emailId) {
        List<ExtractionTask> tasks = taskRepository.findByEmailIdOrderByCreatedAtAsc(emailId);

        long total = tasks.size();
        long completed = tasks.stream()
                .filter(t -> t.getStatus() == ExtractionStatus.COMPLETED)
                .count();
        long failed = tasks.stream()
                .filter(t -> t.getStatus() == ExtractionStatus.FAILED)
                .count();
        long pending = tasks.stream()
                .filter(t -> t.getStatus() == ExtractionStatus.PENDING ||
                             t.getStatus() == ExtractionStatus.RETRYING)
                .count();
        long processing = tasks.stream()
                .filter(t -> t.getStatus() == ExtractionStatus.PROCESSING)
                .count();

        boolean allDone = tasks.stream().allMatch(ExtractionTask::isTerminal);

        return Map.of(
                "total", total,
                "completed", completed,
                "failed", failed,
                "pending", pending,
                "processing", processing,
                "all_done", allDone,
                "success_rate", total > 0 ? (completed * 100.0 / total) : 0.0
        );
    }

    /**
     * Obtener tareas de un email
     */
    public List<ExtractionTask> getEmailTasks(Long emailId) {
        return taskRepository.findByEmailIdOrderByCreatedAtAsc(emailId);
    }

    /**
     * Verificar si un email está completamente procesado
     */
    public boolean isEmailFullyProcessed(Long emailId) {
        return taskRepository.isEmailFullyProcessed(emailId);
    }

    /**
     * Obtener estadísticas globales
     */
    public Map<String, Object> getGlobalStats() {
        long pending = taskRepository.countPendingTasks();
        long processing = taskRepository.countByStatus(ExtractionStatus.PROCESSING);
        long completed = taskRepository.countByStatus(ExtractionStatus.COMPLETED);
        long failed = taskRepository.countByStatus(ExtractionStatus.FAILED);

        return Map.of(
                "pending", pending,
                "processing", processing,
                "completed", completed,
                "failed", failed,
                "total", pending + processing + completed + failed
        );
    }
}
