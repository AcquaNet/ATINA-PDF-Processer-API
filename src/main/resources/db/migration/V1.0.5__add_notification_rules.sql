CREATE TABLE tenant_notification_rules (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    event VARCHAR(50) NOT NULL,
    recipient_type VARCHAR(50) NOT NULL,
    channel VARCHAR(50) NOT NULL,
    channel_config JSON,
    template_name VARCHAR(100),
    subject_template VARCHAR(200),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_notification_rule_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT uk_notification_rule UNIQUE (tenant_id, event, recipient_type, channel)
);

CREATE TABLE webhook_callback_responses (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    correlation_id VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL,
    reference VARCHAR(500),
    message TEXT,
    received_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_callback_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    INDEX idx_callback_correlation (correlation_id)
);
