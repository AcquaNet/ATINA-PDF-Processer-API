package com.atina.invoice.api.controller;

import com.atina.invoice.api.model.WebhookEvent;
import com.atina.invoice.api.model.enums.WebhookEventStatus;
import com.atina.invoice.api.repository.WebhookEventRepository;
import com.atina.invoice.api.service.WebhookProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller para administración de eventos de webhook
 *
 * Endpoints para:
 * - Listar eventos de webhook con filtros
 * - Ver detalles de un evento
 * - Reintentar eventos fallidos
 * - Ver estadísticas
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/webhook-events")
@RequiredArgsConstructor
public class WebhookEventController {

    private final WebhookEventRepository webhookEventRepository;
    private final WebhookProcessor webhookProcessor;

    /**
     * Listar eventos de webhook con paginación y filtros
     *
     * @param status Estado del webhook (opcional)
     * @param tenantId ID del tenant (opcional)
     * @param page Número de página (default: 0)
     * @param size Tamaño de página (default: 20)
     * @return Página de eventos de webhook
     */
    @GetMapping
    public ResponseEntity<Page<WebhookEvent>> listEvents(
            @RequestParam(required = false) WebhookEventStatus status,
            @RequestParam(required = false) Long tenantId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("createdAt").descending());

        // Build specification for filtering
        Specification<WebhookEvent> spec = Specification.where(null);

        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }

        if (tenantId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("tenantId"), tenantId));
        }

        Page<WebhookEvent> events = webhookEventRepository.findAll(spec, pageRequest);

        return ResponseEntity.ok(events);
    }

    /**
     * Obtener un evento de webhook por ID
     *
     * @param id ID del evento
     * @return Evento de webhook
     */
    @GetMapping("/{id}")
    public ResponseEntity<WebhookEvent> getEvent(@PathVariable Long id) {
        return webhookEventRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Obtener eventos por entidad relacionada
     *
     * @param entityType Tipo de entidad (ej: "ProcessedEmail")
     * @param entityId ID de la entidad
     * @return Lista de eventos relacionados
     */
    @GetMapping("/entity/{entityType}/{entityId}")
    public ResponseEntity<List<WebhookEvent>> getEventsByEntity(
            @PathVariable String entityType,
            @PathVariable Long entityId) {

        List<WebhookEvent> events = webhookEventRepository
                .findByEntityTypeAndEntityId(entityType, entityId);

        return ResponseEntity.ok(events);
    }

    /**
     * Reintentar un evento de webhook fallido específico
     *
     * @param id ID del evento
     * @return Mensaje de confirmación
     */
    @PostMapping("/{id}/retry")
    public ResponseEntity<Map<String, String>> retryEvent(@PathVariable Long id) {
        try {
            webhookProcessor.retryWebhookEvent(id);

            Map<String, String> response = new HashMap<>();
            response.put("message", "Webhook event queued for retry");
            response.put("event_id", id.toString());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to retry webhook event {}: {}", id, e.getMessage());

            Map<String, String> response = new HashMap<>();
            response.put("error", e.getMessage());

            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Reintentar todos los webhooks fallidos
     *
     * @return Número de webhooks reintentados
     */
    @PostMapping("/retry-all-failed")
    public ResponseEntity<Map<String, Object>> retryAllFailed() {
        int count = webhookProcessor.retryFailedWebhooks();

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Queued failed webhooks for retry");
        response.put("count", count);

        return ResponseEntity.ok(response);
    }

    /**
     * Obtener estadísticas de webhooks
     *
     * @return Estadísticas por estado
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        Map<String, Object> stats = new HashMap<>();

        // Count by status
        Map<String, Long> statusCounts = new HashMap<>();
        for (WebhookEventStatus status : WebhookEventStatus.values()) {
            long count = webhookEventRepository.countByStatus(status);
            statusCounts.put(status.name().toLowerCase(), count);
        }

        stats.put("by_status", statusCounts);
        stats.put("total", webhookEventRepository.count());

        return ResponseEntity.ok(stats);
    }

    /**
     * Obtener estadísticas por tenant
     *
     * @param tenantId ID del tenant
     * @return Estadísticas del tenant
     */
    @GetMapping("/stats/tenant/{tenantId}")
    public ResponseEntity<Map<String, Object>> getStatsByTenant(@PathVariable Long tenantId) {
        Map<String, Object> stats = new HashMap<>();

        Map<String, Long> statusCounts = new HashMap<>();
        for (WebhookEventStatus status : WebhookEventStatus.values()) {
            long count = webhookEventRepository.countByTenantIdAndStatus(tenantId, status);
            statusCounts.put(status.name().toLowerCase(), count);
        }

        stats.put("tenant_id", tenantId);
        stats.put("by_status", statusCounts);

        return ResponseEntity.ok(stats);
    }
}
