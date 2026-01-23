package com.atina.invoice.api.repository;

import com.atina.invoice.api.model.ProcessedAttachment;
import com.atina.invoice.api.model.enums.AttachmentProcessingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProcessedAttachmentRepository extends JpaRepository<ProcessedAttachment, Long> {

    /**
     * Listar attachments de un email procesado
     */
    List<ProcessedAttachment> findByProcessedEmailId(Long processedEmailId);

    /**
     * Listar attachments por estado
     */
    List<ProcessedAttachment> findByProcessingStatus(AttachmentProcessingStatus status);

    /**
     * Listar attachments que matchearon reglas y están pendientes de extracción
     */
    @Query("SELECT a FROM ProcessedAttachment a WHERE a.rule IS NOT NULL " +
           "AND a.processingStatus = 'DOWNLOADED' " +
           "AND a.extractionJobId IS NULL")
    List<ProcessedAttachment> findAttachmentsPendingExtraction();

    /**
     * Listar attachments de un email que matchearon reglas
     */
    @Query("SELECT a FROM ProcessedAttachment a WHERE a.processedEmail.id = :emailId " +
           "AND a.rule IS NOT NULL")
    List<ProcessedAttachment> findMatchedAttachmentsByEmail(Long emailId);

    /**
     * Contar attachments por estado
     */
    long countByProcessingStatus(AttachmentProcessingStatus status);

    /**
     * Contar attachments procesados de un email
     */
    @Query("SELECT COUNT(a) FROM ProcessedAttachment a WHERE a.processedEmail.id = :emailId " +
           "AND a.rule IS NOT NULL")
    long countMatchedAttachmentsByEmail(Long emailId);
}
