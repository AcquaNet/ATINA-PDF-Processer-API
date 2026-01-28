package com.atina.invoice.api.repository;

import com.atina.invoice.api.model.WebhookEvent;
import com.atina.invoice.api.model.enums.WebhookEventStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * Repository para WebhookEvent
 *
 * Maneja la persistencia de eventos de webhook usando el patrón Transactional Outbox
 */
@Repository
public interface WebhookEventRepository extends JpaRepository<WebhookEvent, Long>, JpaSpecificationExecutor<WebhookEvent> {

    /**
     * Buscar eventos de webhook pendientes listos para ser enviados
     *
     * Criterios:
     * - status = PENDING
     * - next_retry_at IS NULL (nunca intentado) O next_retry_at <= now (listo para retry)
     *
     * Ordenados por created_at ASC (FIFO - los más antiguos primero)
     *
     * @param now Fecha/hora actual
     * @param pageable Paginación (típicamente PageRequest.of(0, batchSize))
     * @return Lista de eventos listos para enviar
     */
    @Query("SELECT w FROM WebhookEvent w WHERE " +
           "w.status = 'PENDING' AND " +
           "(w.nextRetryAt IS NULL OR w.nextRetryAt <= :now) " +
           "ORDER BY w.createdAt ASC")
    List<WebhookEvent> findPendingEvents(@Param("now") Instant now, Pageable pageable);

    /**
     * Buscar eventos por estado
     *
     * @param status Estado del webhook
     * @return Lista de eventos con ese estado
     */
    List<WebhookEvent> findByStatus(WebhookEventStatus status);

    /**
     * Buscar eventos por entidad relacionada
     *
     * Útil para troubleshooting: ver todos los webhooks de un email o tarea específica
     *
     * @param entityType Tipo de entidad (ej: "ProcessedEmail")
     * @param entityId ID de la entidad
     * @return Lista de eventos relacionados con esa entidad
     */
    List<WebhookEvent> findByEntityTypeAndEntityId(String entityType, Long entityId);

    /**
     * Buscar eventos por tenant
     *
     * @param tenantId ID del tenant
     * @return Lista de eventos del tenant
     */
    List<WebhookEvent> findByTenantId(Long tenantId);

    /**
     * Contar eventos por estado
     *
     * Útil para métricas y monitoreo
     *
     * @param status Estado del webhook
     * @return Número de eventos en ese estado
     */
    long countByStatus(WebhookEventStatus status);

    /**
     * Contar eventos pendientes de un tenant
     *
     * @param tenantId ID del tenant
     * @param status Estado
     * @return Número de eventos
     */
    long countByTenantIdAndStatus(Long tenantId, WebhookEventStatus status);

    /**
     * Buscar eventos antiguos para cleanup (opcional)
     *
     * Útil si quieres archivar/eliminar eventos SENT muy antiguos
     *
     * @param threshold Fecha límite
     * @param status Estado de los eventos a buscar
     * @return Lista de eventos antiguos
     */
    @Query("SELECT w FROM WebhookEvent w WHERE " +
           "w.status = :status AND " +
           "w.createdAt < :threshold " +
           "ORDER BY w.createdAt ASC")
    List<WebhookEvent> findOldEvents(
            @Param("threshold") Instant threshold,
            @Param("status") WebhookEventStatus status,
            Pageable pageable);
}
