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
     *
     * IMPORTANTE: Usa JOIN FETCH para cargar relaciones y evitar LazyInitializationException
     */
    @Query("SELECT t FROM ExtractionTask t " +
           "LEFT JOIN FETCH t.email e " +
           "LEFT JOIN FETCH e.tenant " +
           "LEFT JOIN FETCH t.attachment " +
           "WHERE t.email.id = :emailId " +
           "ORDER BY t.createdAt ASC")
    List<ExtractionTask> findByEmailIdOrderByCreatedAtAsc(@Param("emailId") Long emailId);

    /**
     * Buscar próximas tareas a procesar
     * Incluye:
     * - Tareas PENDING
     * - Tareas RETRYING cuyo nextRetryAt ya pasó
     * Ordenadas por prioridad (mayor primero) y luego por fecha creación
     *
     * IMPORTANTE: Usa JOIN FETCH para cargar email y tenant en la misma query
     * y evitar LazyInitializationException
     *
     * @param now Fecha/hora actual
     * @return Lista de tareas listas para procesar
     */
    @Query("SELECT t FROM ExtractionTask t " +
           "LEFT JOIN FETCH t.email e " +
           "LEFT JOIN FETCH e.tenant " +
           "WHERE (t.status = 'PENDING' OR " +
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
     * IMPORTANTE: Usa JOIN FETCH para cargar relaciones y evitar LazyInitializationException
     *
     * @param threshold Instante antes del cual se considera atascada
     * @return Tareas en PROCESSING desde antes del threshold
     */
    @Query("SELECT t FROM ExtractionTask t " +
           "LEFT JOIN FETCH t.email e " +
           "LEFT JOIN FETCH e.tenant " +
           "WHERE t.status = 'PROCESSING' AND t.startedAt < :threshold")
    List<ExtractionTask> findStuckTasks(@Param("threshold") Instant threshold);

    /**
     * Buscar tareas por estado
     */
    List<ExtractionTask> findByStatus(ExtractionStatus status);

    /**
     * Buscar una tarea por ID con todas sus relaciones cargadas
     * IMPORTANTE: Usa JOIN FETCH para evitar LazyInitializationException
     */
    @Query("SELECT t FROM ExtractionTask t " +
           "LEFT JOIN FETCH t.email e " +
           "LEFT JOIN FETCH e.tenant " +
           "LEFT JOIN FETCH t.attachment " +
           "WHERE t.id = :id")
    ExtractionTask findByIdWithRelations(@Param("id") Long id);

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

    /**
     * Buscar todas las tareas con un correlationId específico
     * Útil para troubleshooting y tracking de una tarea específica a través de reintentos
     *
     * @param correlationId Correlation ID de la tarea
     * @return Lista de tareas (normalmente 1, pero puede ser más si hay datos duplicados)
     */
    List<ExtractionTask> findByCorrelationId(String correlationId);
}
