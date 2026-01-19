package com.atina.invoice.api.repository;

import com.atina.invoice.api.model.EmailAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmailAccountRepository extends JpaRepository<EmailAccount, Long> {
    
    /**
     * Buscar por tenant y email
     */
    Optional<EmailAccount> findByTenantIdAndEmailAddress(Long tenantId, String emailAddress);
    
    /**
     * Listar todas las cuentas de un tenant
     */
    List<EmailAccount> findByTenantId(Long tenantId);
    
    /**
     * Listar cuentas habilitadas de un tenant
     */
    List<EmailAccount> findByTenantIdAndEnabled(Long tenantId, Boolean enabled);
    
    /**
     * Listar cuentas con polling habilitado
     */
    @Query("SELECT e FROM EmailAccount e WHERE e.enabled = true AND e.pollingEnabled = true")
    List<EmailAccount> findAllWithPollingEnabled();
    
    /**
     * Verificar si existe una cuenta con ese email en el tenant
     */
    boolean existsByTenantIdAndEmailAddress(Long tenantId, String emailAddress);
}
