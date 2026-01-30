package com.atina.invoice.api.service;

import com.atina.invoice.api.model.TenantNotificationRule;
import com.atina.invoice.api.model.enums.NotificationChannel;
import com.atina.invoice.api.model.enums.NotificationEvent;
import com.atina.invoice.api.repository.TenantNotificationRuleRepository;
import com.atina.invoice.api.service.notification.NotificationChannelSender;
import com.atina.invoice.api.service.notification.NotificationContext;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class NotificationDispatcher {

    private final TenantNotificationRuleRepository ruleRepository;
    private final Map<NotificationChannel, NotificationChannelSender> senderMap;

    public NotificationDispatcher(TenantNotificationRuleRepository ruleRepository,
                                  List<NotificationChannelSender> senders) {
        this.ruleRepository = ruleRepository;
        this.senderMap = new EnumMap<>(NotificationChannel.class);
        for (NotificationChannelSender sender : senders) {
            this.senderMap.put(sender.getChannel(), sender);
        }
    }

    @PostConstruct
    public void init() {
        log.info("NotificationDispatcher initialized with {} channel senders: {}",
                senderMap.size(), senderMap.keySet());
    }

    @Async
    public void dispatch(NotificationEvent event, NotificationContext context) {
        Long tenantId = context.getTenant().getId();

        log.info("Dispatching notification event {} for tenant {}", event, tenantId);

        List<TenantNotificationRule> rules = ruleRepository
                .findByTenantIdAndEventAndEnabled(tenantId, event, true);

        if (rules.isEmpty()) {
            log.debug("No active notification rules for tenant {} and event {}", tenantId, event);
            return;
        }

        log.info("Found {} notification rules for event {} (tenant {})", rules.size(), event, tenantId);

        for (TenantNotificationRule rule : rules) {
            NotificationChannelSender sender = senderMap.get(rule.getChannel());
            if (sender == null) {
                log.warn("No sender registered for channel {}, skipping rule {}", rule.getChannel(), rule.getId());
                continue;
            }
            try {
                sender.send(context, rule);
            } catch (Exception e) {
                log.error("Failed to send notification via {} for rule {}: {}",
                        rule.getChannel(), rule.getId(), e.getMessage(), e);
            }
        }
    }
}
