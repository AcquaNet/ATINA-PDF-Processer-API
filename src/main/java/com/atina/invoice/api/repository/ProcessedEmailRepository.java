package com.atina.invoice.api.repository;

import com.atina.invoice.api.model.ProcessedEmail;
import com.atina.invoice.api.model.enums.EmailProcessingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProcessedEmailRepository extends JpaRepository<ProcessedEmail, Long> {

    /**
     * ⭐ NUEVO: Verificar si ya existe un email procesado por account y UID
     * Usado para evitar reprocesar el mismo email
     */
    boolean existsByEmailAccountIdAndEmailUid(Long emailAccountId, String emailUid);

    /**
     * Buscar email por account y UID
     */
    Optional<ProcessedEmail> findByEmailAccountIdAndEmailUid(Long emailAccountId, String emailUid);

    /**
     * Listar emails de una cuenta
     */
    List<ProcessedEmail> findByEmailAccountId(Long emailAccountId);

    /**
     * Listar emails de un tenant
     */
    List<ProcessedEmail> findByTenantId(Long tenantId);

    /**
     * Listar emails por estado
     */
    List<ProcessedEmail> findByProcessingStatus(EmailProcessingStatus status);

    /**
     * Listar emails de un sender rule
     */
    List<ProcessedEmail> findBySenderRuleId(Long senderRuleId);

    /**
     * Listar emails sin sender rule (ignorados)
     */
    List<ProcessedEmail> findBySenderRuleIsNull();

    /**
     * Contar emails por estado
     */
    long countByProcessingStatus(EmailProcessingStatus status);

    /**
     * Contar emails de una cuenta
     */
    long countByEmailAccountId(Long emailAccountId);

    /**
     * Buscar emails con attachments procesados
     */
    @Query("SELECT e FROM ProcessedEmail e WHERE e.processedAttachments > 0")
    List<ProcessedEmail> findEmailsWithProcessedAttachments();

    /**
     * Buscar emails pendientes de procesamiento
     */
    @Query("SELECT e FROM ProcessedEmail e WHERE e.processingStatus = 'PROCESSING' " +
            "OR e.processingStatus = 'PENDING'")
    List<ProcessedEmail> findPendingEmails();

    /**
     * ⭐ CRITICAL: Buscar email por ID con attachments cargados (EAGER)
     *
     * Sin JOIN FETCH, getAttachments() retorna lista vacía debido a lazy loading.
     * Este método carga los attachments en la misma query.
     *
     * Usar este método antes de generar metadata.
     */
    @Query("SELECT e FROM ProcessedEmail e " +
            "LEFT JOIN FETCH e.attachments " +
            "WHERE e.id = :id")
    Optional<ProcessedEmail> findByIdWithAttachments(@org.springframework.data.repository.query.Param("id") Long id);

    /**
     * Buscar email por ID con tenant y senderRule cargados (EAGER)
     *
     * Usado por ExtractionWorker.checkEmailCompletion() para evitar LazyInitializationException
     * cuando se accede a email.getTenant() y email.getSenderRule()
     */
    @Query("SELECT e FROM ProcessedEmail e " +
            "LEFT JOIN FETCH e.tenant " +
            "LEFT JOIN FETCH e.senderRule " +
            "WHERE e.id = :id")
    Optional<ProcessedEmail> findByIdWithRelations(@org.springframework.data.repository.query.Param("id") Long id);
}
