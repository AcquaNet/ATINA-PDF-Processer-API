package com.atina.invoice.api.repository;

import com.atina.invoice.api.model.ExtractionTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for ExtractionTemplate
 */
@Repository
public interface ExtractionTemplateRepository extends JpaRepository<ExtractionTemplate, Long> {

    /**
     * Buscar template activo por tenant y source
     * Este es el método principal para encontrar qué template usar
     *
     * @param tenantId ID del tenant
     * @param source Source del documento (ej: "JDE", "SAP")
     * @param isActive Si debe estar activo
     * @return Template si existe
     */
    Optional<ExtractionTemplate> findByTenantIdAndSourceAndIsActive(
            Long tenantId,
            String source,
            Boolean isActive
    );

    /**
     * Buscar template por tenant y source (sin filtrar por activo)
     */
    Optional<ExtractionTemplate> findByTenantIdAndSource(Long tenantId, String source);

    /**
     * Listar todos los templates activos de un tenant
     */
    List<ExtractionTemplate> findByTenantIdAndIsActive(Long tenantId, Boolean isActive);

    /**
     * Listar todos los templates de un tenant
     */
    List<ExtractionTemplate> findByTenantId(Long tenantId);

    /**
     * Listar templates por source (across tenants)
     */
    List<ExtractionTemplate> findBySource(String source);

    /**
     * Verificar si existe template para tenant y source
     */
    boolean existsByTenantIdAndSource(Long tenantId, String source);

    /**
     * Contar templates activos por tenant
     */
    @Query("SELECT COUNT(t) FROM ExtractionTemplate t " +
           "WHERE t.tenant.id = :tenantId AND t.isActive = true")
    long countActiveTemplatesByTenant(@Param("tenantId") Long tenantId);

    /**
     * Buscar templates inactivos (para limpieza)
     */
    List<ExtractionTemplate> findByIsActive(Boolean isActive);
}
