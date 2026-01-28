package com.atina.invoice.api.model.enums;

/**
 * Estado de un evento de webhook en el patrón Transactional Outbox
 *
 * Flujo normal:
 *   PENDING → SENDING → SENT
 *
 * Flujo con fallo y retry:
 *   PENDING → SENDING → PENDING (retry) → SENDING → SENT
 *
 * Flujo con fallo permanente:
 *   PENDING → SENDING → FAILED (después de max_attempts)
 */
public enum WebhookEventStatus {

    /**
     * Evento creado, esperando ser enviado
     * El WebhookProcessor lo recogerá en el próximo ciclo
     */
    PENDING,

    /**
     * Evento en proceso de envío
     * El webhook HTTP está siendo enviado en este momento
     */
    SENDING,

    /**
     * Webhook enviado exitosamente
     * Estado final - el webhook fue entregado con éxito
     */
    SENT,

    /**
     * Webhook falló después de todos los reintentos
     * Estado final - requiere intervención manual
     */
    FAILED
}
