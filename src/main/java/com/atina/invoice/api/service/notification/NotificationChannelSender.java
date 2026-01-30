package com.atina.invoice.api.service.notification;

import com.atina.invoice.api.model.TenantNotificationRule;
import com.atina.invoice.api.model.enums.NotificationChannel;

public interface NotificationChannelSender {

    NotificationChannel getChannel();

    void send(NotificationContext context, TenantNotificationRule rule);
}
