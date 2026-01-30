package com.atina.invoice.api.model;

import com.atina.invoice.api.model.enums.NotificationChannel;
import com.atina.invoice.api.model.enums.NotificationEvent;
import com.atina.invoice.api.model.enums.NotificationRecipientType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "tenant_notification_rules",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_notification_rule",
                        columnNames = {"tenant_id", "event", "recipient_type", "channel"})
        })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantNotificationRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Enumerated(EnumType.STRING)
    @Column(name = "event", nullable = false, length = 50)
    private NotificationEvent event;

    @Enumerated(EnumType.STRING)
    @Column(name = "recipient_type", nullable = false, length = 50)
    private NotificationRecipientType recipientType;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 50)
    private NotificationChannel channel;

    @Column(name = "channel_config", columnDefinition = "JSON")
    private String channelConfig;

    @Column(name = "template_name", length = 100)
    private String templateName;

    @Column(name = "subject_template", length = 200)
    private String subjectTemplate;

    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
