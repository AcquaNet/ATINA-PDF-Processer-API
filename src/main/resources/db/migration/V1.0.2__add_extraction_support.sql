-- ========================================================================
-- MIGRACIÓN: Agregar soporte para extracción asíncrona de PDFs
-- Versión: 1.0.2
-- Descripción: Agrega tablas y campos para sistema de extracción asíncrona
-- ========================================================================

-- ========================================================================
-- 1. AGREGAR CAMPOS A TABLAS EXISTENTES
-- ========================================================================

-- Agregar webhook_url a tenants
ALTER TABLE tenants
    ADD COLUMN webhook_url VARCHAR(500) COMMENT 'URL para notificaciones webhook de extracción completada';

-- Agregar notification_email a email_sender_rules
ALTER TABLE email_sender_rules
    ADD COLUMN notification_email VARCHAR(255) COMMENT 'Email adicional para recibir notificaciones (CC)';

-- ========================================================================
-- 2. CREAR TABLA extraction_templates
-- ========================================================================

CREATE TABLE extraction_templates (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    source VARCHAR(50) NOT NULL COMMENT 'Source del documento (JDE, SAP, etc) - debe coincidir con AttachmentProcessingRule.source',
    template_name VARCHAR(255) NOT NULL COMMENT 'Nombre del archivo template JSON - el path completo se construye con tenant.templateBasePath + templateName',
    is_active BOOLEAN NOT NULL DEFAULT true COMMENT 'Si el template está activo',
    description VARCHAR(255) COMMENT 'Descripción del template',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Fecha de creación',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Fecha de última actualización',

    -- Foreign Keys
    CONSTRAINT fk_extraction_template_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants(id)
        ON DELETE CASCADE,

    -- Constraints
    CONSTRAINT uk_tenant_source UNIQUE (tenant_id, source),

    -- Indexes
    INDEX idx_tenant (tenant_id),
    INDEX idx_source (source),
    INDEX idx_is_active (is_active)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Templates de extracción para PDFs - mapea (tenant, source) a template JSON';

-- ========================================================================
-- 3. CREAR TABLA extraction_tasks
-- ========================================================================

CREATE TABLE extraction_tasks (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    processed_attachment_id BIGINT NOT NULL COMMENT 'Attachment del que se extrae información',
    processed_email_id BIGINT NOT NULL COMMENT 'Email al que pertenece el attachment',
    pdf_path VARCHAR(500) NOT NULL COMMENT 'Path al PDF a procesar',
    source VARCHAR(50) NOT NULL COMMENT 'Source del documento - usado para buscar template',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'Estado de la tarea: PENDING, PROCESSING, RETRYING, COMPLETED, FAILED, CANCELLED',
    priority INT NOT NULL DEFAULT 0 COMMENT 'Prioridad (mayor = más prioritario)',
    result_path VARCHAR(500) COMMENT 'Path al archivo JSON con resultado de extracción',
    raw_result TEXT COMMENT 'Resultado JSON en texto (backup)',
    error_message TEXT COMMENT 'Mensaje de error si falló',
    attempts INT NOT NULL DEFAULT 0 COMMENT 'Número de intentos realizados',
    max_attempts INT NOT NULL DEFAULT 3 COMMENT 'Máximo número de intentos',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Fecha de creación',
    started_at TIMESTAMP NULL COMMENT 'Fecha en que comenzó el procesamiento',
    completed_at TIMESTAMP NULL COMMENT 'Fecha en que completó',
    next_retry_at TIMESTAMP NULL COMMENT 'Fecha del próximo reintento (si está en RETRYING)',

    -- Foreign Keys
    CONSTRAINT fk_extraction_task_attachment
        FOREIGN KEY (processed_attachment_id) REFERENCES processed_attachments(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_extraction_task_email
        FOREIGN KEY (processed_email_id) REFERENCES processed_emails(id)
        ON DELETE CASCADE,

    -- Indexes
    INDEX idx_status (status),
    INDEX idx_email (processed_email_id),
    INDEX idx_attachment (processed_attachment_id),
    INDEX idx_next_retry (next_retry_at),
    INDEX idx_priority_created (priority DESC, created_at ASC),
    INDEX idx_source (source)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Tareas de extracción asíncrona de PDFs';

-- ========================================================================
-- 4. DATOS DE EJEMPLO (OPCIONAL - COMENTADO)
-- ========================================================================

-- Descomentar y ajustar según necesidades:
-- El path completo se construye como: {tenant.templateBasePath}/{template_name}
-- Ejemplo: /config/templates/jde_invoice_template.json
--
-- INSERT INTO extraction_templates (tenant_id, source, template_name, is_active, description) VALUES
-- (1, 'JDE', 'jde_invoice_template.json', true, 'Template para facturas JDE'),
-- (1, 'SAP', 'sap_invoice_template.json', true, 'Template para facturas SAP'),
-- (1, 'invoices', 'generic_invoice_template.json', true, 'Template genérico para facturas');

-- ========================================================================
-- 5. COMENTARIOS
-- ========================================================================

ALTER TABLE extraction_templates COMMENT = 'Templates de extracción para PDFs - mapea (tenant, source) a template JSON';
ALTER TABLE extraction_tasks COMMENT = 'Cola de tareas de extracción asíncrona de PDFs';
