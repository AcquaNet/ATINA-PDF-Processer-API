package com.atina.invoice.api.repository;

import com.atina.invoice.api.model.TenantNotificationRule;
import com.atina.invoice.api.model.enums.NotificationEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TenantNotificationRuleRepository extends JpaRepository<TenantNotificationRule, Long> {

    List<TenantNotificationRule> findByTenantIdAndEventAndEnabled(Long tenantId, NotificationEvent event, Boolean enabled);

    List<TenantNotificationRule> findByTenantId(Long tenantId);
}
