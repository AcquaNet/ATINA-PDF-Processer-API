package com.atina.invoice.api.repository;

import com.atina.invoice.api.model.EmailSenderRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmailSenderRuleRepository extends JpaRepository<EmailSenderRule, Long> {
    
    /**
     * Buscar regla por tenant, cuenta y email del sender
     */
    Optional<EmailSenderRule> findByTenantIdAndEmailAccountIdAndSenderEmail(
            Long tenantId, Long emailAccountId, String senderEmail);
    
    /**
     * Buscar regla por email account y sender email
     */
    Optional<EmailSenderRule> findByEmailAccountIdAndSenderEmail(Long emailAccountId, String senderEmail);
    
    /**
     * Listar reglas de un tenant
     */
    List<EmailSenderRule> findByTenantId(Long tenantId);
    
    /**
     * Listar reglas de una cuenta de email
     */
    List<EmailSenderRule> findByEmailAccountId(Long emailAccountId);
    
    /**
     * Listar reglas habilitadas de un tenant
     */
    List<EmailSenderRule> findByTenantIdAndEnabled(Long tenantId, Boolean enabled);
    
    /**
     * Verificar si existe una regla para ese sender
     */
    boolean existsByEmailAccountIdAndSenderEmail(Long emailAccountId, String senderEmail);
}
