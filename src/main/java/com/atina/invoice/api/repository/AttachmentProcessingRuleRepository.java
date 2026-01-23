package com.atina.invoice.api.repository;

import com.atina.invoice.api.model.AttachmentProcessingRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AttachmentProcessingRuleRepository extends JpaRepository<AttachmentProcessingRule, Long> {
    
    /**
     * Listar reglas de un sender rule, ordenadas por ruleOrder
     */
    List<AttachmentProcessingRule> findBySenderRuleIdOrderByRuleOrderAsc(Long senderRuleId);
    
    /**
     * Listar reglas habilitadas de un sender rule
     */
    List<AttachmentProcessingRule> findBySenderRuleIdAndEnabledOrderByRuleOrderAsc(Long senderRuleId, Boolean enabled);
    
    /**
     * Buscar por sender rule y orden
     */
    Optional<AttachmentProcessingRule> findBySenderRuleIdAndRuleOrder(Long senderRuleId, Integer ruleOrder);
    
    /**
     * Verificar si existe una regla con ese orden
     */
    boolean existsBySenderRuleIdAndRuleOrder(Long senderRuleId, Integer ruleOrder);
    
    /**
     * Obtener el máximo orden para un sender rule
     */
    Optional<Integer> findMaxRuleOrderBySenderRuleId(Long senderRuleId);
}
