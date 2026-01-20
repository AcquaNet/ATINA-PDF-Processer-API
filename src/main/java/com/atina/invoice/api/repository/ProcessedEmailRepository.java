package com.atina.invoice.api.repository;

import com.atina.invoice.api.model.ProcessedEmail;
import com.atina.invoice.api.model.enums.EmailProcessingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProcessedEmailRepository extends JpaRepository<ProcessedEmail, Long> {

    /**
     * Buscar por tenant y email account UID
     */
    Optional<ProcessedEmail> findByEmailAccountIdAndEmailUid(Long emailAccountId, String emailUid);

    /**
     * Verificar si ya existe un email procesado
     */
    boolean existsByEmailAccountIdAndEmailUid(Long emailAccountId, String emailUid);

    /**
     * Listar emails procesados por tenant
     */
    Page<ProcessedEmail> findByTenantId(Long tenantId, Pageable pageable);

    /**
     * Listar emails procesados por email account
     */
    Page<ProcessedEmail> findByEmailAccountId(Long emailAccountId, Pageable pageable);

    /**
     * Listar emails por estado
     */
    List<ProcessedEmail> findByProcessingStatus(EmailProcessingStatus status);

    /**
     * Listar emails por tenant y estado
     */
    Page<ProcessedEmail> findByTenantIdAndProcessingStatus(
            Long tenantId, EmailProcessingStatus status, Pageable pageable);

    /**
     * Listar emails procesados desde una fecha
     */
    List<ProcessedEmail> findByProcessedDateAfter(Instant date);

    /**
     * Listar emails sin notificación de recepción enviada
     */
    @Query("SELECT e FROM ProcessedEmail e WHERE e.receivedNotificationSent = false " +
           "AND e.processingStatus != 'IGNORED' " +
           "AND e.senderRule IS NOT NULL " +
           "AND e.senderRule.autoReplyEnabled = true")
    List<ProcessedEmail> findEmailsPendingReceivedNotification();

    /**
     * Listar emails sin notificación de procesamiento enviada
     */
    @Query("SELECT e FROM ProcessedEmail e WHERE e.processedNotificationSent = false " +
           "AND e.processingStatus = 'COMPLETED' " +
           "AND e.senderRule IS NOT NULL " +
           "AND e.senderRule.autoReplyEnabled = true")
    List<ProcessedEmail> findEmailsPendingProcessedNotification();

    /**
     * Contar emails por estado
     */
    long countByProcessingStatus(EmailProcessingStatus status);

    /**
     * Contar emails procesados hoy
     */
    @Query("SELECT COUNT(e) FROM ProcessedEmail e WHERE e.processedDate >= :startOfDay")
    long countProcessedToday(Instant startOfDay);
}
