package com.atina.invoice.api.repository;

import com.atina.invoice.api.model.ExtractionTask;
import com.atina.invoice.api.model.enums.ExtractionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * Repository for ExtractionTask
 */
@Repository
public interface ExtractionTaskRepository extends JpaRepository<ExtractionTask, Long> {

    /**
     * Buscar tareas de un email específico
     * Útil para verificar si todas las tareas de un email completaron
     */
    List<ExtractionTask> findByEmailIdOrderByCreatedAtAsc(Long emailId);

    /**
     * Buscar próximas tareas a procesar
     * Incluye:
     * - Tareas PENDING
     * - Tareas RETRYING cuyo nextRetryAt ya pasó
     * Ordenadas por prioridad (mayor primero) y luego por fecha creación
     *
     * @param now Fecha/hora actual
     * @return Lista de tareas listas para procesar
     */
    @Query("SELECT t FROM ExtractionTask t WHERE " +
           "(t.status = 'PENDING' OR " +
           " (t.status = 'RETRYING' AND t.nextRetryAt <= :now)) " +
           "ORDER BY t.priority DESC, t.createdAt ASC")
    List<ExtractionTask> findNextTasksToProcess(@Param("now") Instant now);

    /**
     * Contar tareas de un email por estado
     */
    long countByEmailIdAndStatus(Long emailId, ExtractionStatus status);

    /**
     * Buscar tareas "atascadas" (PROCESSING por demasiado tiempo)
     * Útil para recovery/cleanup
     *
     * @param threshold Instante antes del cual se considera atascada
     * @return Tareas en PROCESSING desde antes del threshold
     */
    @Query("SELECT t FROM ExtractionTask t WHERE " +
           "t.status = 'PROCESSING' AND t.startedAt < :threshold")
    List<ExtractionTask> findStuckTasks(@Param("threshold") Instant threshold);

    /**
     * Buscar tareas por estado
     */
    List<ExtractionTask> findByStatus(ExtractionStatus status);

    /**
     * Buscar tareas por attachment
     */
    List<ExtractionTask> findByAttachmentId(Long attachmentId);

    /**
     * Buscar tareas por source
     */
    List<ExtractionTask> findBySource(String source);

    /**
     * Contar tareas pendientes/retry
     */
    @Query("SELECT COUNT(t) FROM ExtractionTask t WHERE " +
           "t.status IN ('PENDING', 'RETRYING')")
    long countPendingTasks();

    /**
     * Contar tareas en procesamiento
     */
    long countByStatus(ExtractionStatus status);

    /**
     * Buscar tareas completadas de un email
     */
    @Query("SELECT t FROM ExtractionTask t WHERE " +
           "t.email.id = :emailId AND t.status = 'COMPLETED'")
    List<ExtractionTask> findCompletedTasksByEmail(@Param("emailId") Long emailId);

    /**
     * Buscar tareas fallidas de un email
     */
    @Query("SELECT t FROM ExtractionTask t WHERE " +
           "t.email.id = :emailId AND t.status = 'FAILED'")
    List<ExtractionTask> findFailedTasksByEmail(@Param("emailId") Long emailId);

    /**
     * Verificar si un email tiene todas sus tareas completadas
     */
    @Query("SELECT CASE WHEN COUNT(t) = 0 THEN true ELSE false END " +
           "FROM ExtractionTask t WHERE " +
           "t.email.id = :emailId AND " +
           "t.status NOT IN ('COMPLETED', 'FAILED', 'CANCELLED')")
    boolean isEmailFullyProcessed(@Param("emailId") Long emailId);

    /**
     * Buscar tareas antiguas para cleanup
     */
    @Query("SELECT t FROM ExtractionTask t WHERE " +
           "t.createdAt < :threshold AND t.status IN ('COMPLETED', 'FAILED', 'CANCELLED')")
    List<ExtractionTask> findOldCompletedTasks(@Param("threshold") Instant threshold);

    /**
     * Estadísticas: contar por estado
     */
    @Query("SELECT t.status, COUNT(t) FROM ExtractionTask t GROUP BY t.status")
    List<Object[]> countByStatusGrouped();

    /**
     * Buscar tareas recientes (últimas N horas)
     */
    @Query("SELECT t FROM ExtractionTask t WHERE t.createdAt >= :since " +
           "ORDER BY t.createdAt DESC")
    List<ExtractionTask> findRecentTasks(@Param("since") Instant since);
}
