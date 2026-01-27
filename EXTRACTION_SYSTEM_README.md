# Sistema de Extracción Asíncrona de PDFs

## 📋 Tabla de Contenidos

- [Descripción General](#descripción-general)
- [Arquitectura del Sistema](#arquitectura-del-sistema)
- [Flujo de Procesamiento](#flujo-de-procesamiento)
- [Componentes Principales](#componentes-principales)
- [Configuración](#configuración)
- [Modelo de Datos](#modelo-de-datos)
- [API Endpoints](#api-endpoints)
- [Guía de Uso](#guía-de-uso)
- [Troubleshooting](#troubleshooting)

---

## 📖 Descripción General

Sistema de extracción asíncrona de datos estructurados desde PDFs recibidos por email. El sistema:

1. **Recibe emails** con attachments PDF vía polling IMAP/POP3
2. **Guarda los PDFs** en el filesystem del tenant
3. **Encola tareas** de extracción en la base de datos
4. **Procesa en background** usando DoclingService + ExtractionService
5. **Notifica resultados** vía webhook HTTP y email

### Características Principales

✅ **Procesamiento Asíncrono** - No bloquea el polling de emails
✅ **Multi-tenant** - Cada tenant tiene sus propios templates y storage
✅ **Retry Logic** - Reintentos automáticos con exponential backoff
✅ **Webhook Notifications** - Notificaciones HTTP POST cuando completa
✅ **Email Notifications** - Emails de recepción y procesamiento
✅ **Template Management** - Templates por tenant y source (JDE, SAP, etc)
✅ **Stuck Task Recovery** - Recuperación automática de tareas atascadas

---

## 🏗️ Arquitectura del Sistema

```
┌─────────────────────────────────────────────────────────────────┐
│                      EMAIL POLLING (Rápido)                     │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
        EmailPollingScheduler (cada 60s)
                              │
                              ▼
        EmailPollingService.pollAllAccounts()
                              │
                              ▼
        EmailProcessingService.processEmail()
                              │
                ┌─────────────┼─────────────┐
                │             │             │
                ▼             ▼             ▼
        Guardar PDF    Crear ProcessedEmail    Crear ProcessedAttachment
        (filesystem)   (status=COMPLETED)      (status=DOWNLOADED)
                              │
                ┌─────────────┴─────────────┐
                ▼                           ▼
    PdfExtractionService          EmailNotificationService
    .enqueueEmailExtractions()    .sendReceivedEmail()
                │                           │
                ▼                           ▼
    Crea ExtractionTask(s)        Email: "PDFs recibidos"
    (status=PENDING)


┌─────────────────────────────────────────────────────────────────┐
│              BACKGROUND EXTRACTION (Cada 5s)                    │
└─────────────────────────────────────────────────────────────────┘
                              │
        ExtractionWorker.processExtractionTasks()
        [@Scheduled(fixedDelay=5000ms)]
                              │
                              ▼
        SELECT tareas PENDING/RETRYING de BD
                              │
                ┌─────────────┴─────────────┐
                │                           │
                ▼                           ▼
        Para cada ExtractionTask:    (batch_size=5)
                │
                ├─► Buscar ExtractionTemplate (tenant+source)
                ├─► Cargar PDF desde filesystem
                ├─► DoclingService.convertPdf(pdf)
                ├─► Cargar template desde filesystem
                ├─► ExtractionService.extract(docling, template)
                ├─► Guardar resultado JSON
                └─► Marcar como COMPLETED
                              │
                              ▼
        checkEmailCompletion(email)
        ¿Todas las tareas completaron?
                              │
                ┌─────────────┴─────────────┐
                │                           │
                ▼ SI                        ▼ NO
    WebhookService              Esperar más tareas
    .sendExtractionCompleted()
                │
                ├─► HTTP POST a tenant.webhookUrl
                │   (con datos extraídos)
                │
                └─► EmailNotificationService
                    .sendProcessedEmail()
                    └─► Email: "PDFs procesados"
```

---

## 🔄 Flujo de Procesamiento

### Fase 1: Recepción de Email (Síncrono - Segundos)

```
1. EmailPollingScheduler ejecuta cada 60s
   ↓
2. Lee emails NO LEÍDOS de cada cuenta habilitada
   ↓
3. Para cada email:
   a. Extrae metadata (from, subject, dates)
   b. Busca EmailSenderRule por fromAddress
   c. Si no hay regla → marca email como IGNORED
   d. Si processEnabled=false → marca como IGNORED
   ↓
4. Para cada attachment:
   a. Verifica si matchea regex de AttachmentProcessingRule
   b. Si matchea → descarga y guarda en filesystem
   c. Nombre normalizado: {senderId}_{emailId}_{seq}_{source}_{dest}_{timestamp}.pdf
   d. Crea ProcessedAttachment (status=DOWNLOADED)
   ↓
5. Genera metadata JSON del email
   ↓
6. Marca ProcessedEmail como COMPLETED
   ↓
7. ⭐ NUEVO: Encola extracciones asíncronas
   → PdfExtractionService.enqueueEmailExtractions()
   → Crea ExtractionTask por cada attachment DOWNLOADED
   ↓
8. ⭐ NUEVO: Envía email de recepción
   → EmailNotificationService.sendReceivedEmail()
   → Email: "Hemos recibido tus PDFs"
   ↓
9. Marca email como LEÍDO (si configurado)
```

### Fase 2: Extracción Asíncrona (Background - Minutos)

```
1. ExtractionWorker ejecuta cada 5s
   ↓
2. Consulta BD: SELECT * FROM extraction_tasks
   WHERE status IN ('PENDING', 'RETRYING')
   ORDER BY priority DESC, created_at ASC
   LIMIT {batch_size}
   ↓
3. Para cada tarea (max 5 en paralelo):
   ↓
   a. Marca como PROCESSING (attempts++)
      ↓
   b. Busca ExtractionTemplate activo
      → findByTenantIdAndSourceAndIsActive(tenant, source, true)
      → Si no existe → ERROR: "No template found"
      ↓
   c. Construye path del template
      → {tenant.storageBasePath}/{tenant.tenantCode}/{tenant.templateBasePath}/{template.templateName}
      → Ejemplo: /tmp/ACME/config/templates/jde_invoice_template.json
      ↓
   d. Carga PDF desde filesystem
      → File(task.pdfPath)
      ↓
   e. Convierte PDF a JSON interno
      → DoclingService.convertPdf(pdfFile)
      → Llama API Docling externa
      ↓
   f. Carga template JSON
      → objectMapper.readTree(templateFile)
      ↓
   g. Extrae datos estructurados
      → ExtractionService.extract(doclingJson, template, options)
      → Usa AI (OpenAI/Anthropic) para extraer campos
      ↓
   h. Guarda resultado JSON en filesystem
      → Path: {storageBasePath}/{tenantCode}/process/extractions/{emailId}_{attachmentId}_extraction.json
      ↓
   i. Actualiza tarea
      → status = COMPLETED
      → resultPath = path del JSON
      → rawResult = JSON string (backup)
      → completedAt = now()
      ↓
   j. Si falla:
      → attempts < maxAttempts (3)
        → status = RETRYING
        → nextRetryAt = now() + exponential_backoff
      → attempts >= maxAttempts
        → status = FAILED
        → errorMessage = error details
   ↓
4. Verifica si email completó
   → ¿Todas las tareas del email están en estado terminal?
   → Estados terminales: COMPLETED, FAILED, CANCELLED
   ↓
5. Si SÍ, todas completaron:
   ↓
   a. Envía Webhook HTTP POST
      → URL: tenant.webhookUrl
      → Payload: email metadata + todas las extracciones
      → Retry: 3 intentos con exponential backoff
      ↓
   b. Envía Email de Procesamiento
      → Para: senderRule.senderEmail
      → CC: senderRule.notificationEmail (si existe)
      → Template: senderRule.templateEmailProcessed
      → Contenido: Resumen de extracciones + datos extraídos
```

---

## 🧩 Componentes Principales

### 1. Entidades

#### ExtractionTemplate
Mapea (tenant, source) → template JSON

```java
{
  id: 1,
  tenant: Tenant,
  source: "JDE",                           // Debe coincidir con AttachmentProcessingRule.source
  templateName: "jde_invoice_template.json",
  isActive: true,
  description: "Template para facturas JDE"
}
```

**Path completo del template:**
```
{tenant.storageBasePath}/{tenant.tenantCode}/{tenant.templateBasePath}/{templateName}

Ejemplo:
/private/tmp/process-mails/ACME/config/templates/jde_invoice_template.json
```

#### ExtractionTask
Cola de tareas de extracción

```java
{
  id: 1,
  attachment: ProcessedAttachment,
  email: ProcessedEmail,
  pdfPath: "/private/tmp/process-mails/ACME/process/inbounds/92455890_123_0001_invoice_jde_2026-01-26.pdf",
  source: "JDE",
  status: "PENDING",        // PENDING → PROCESSING → COMPLETED/FAILED/RETRYING
  priority: 0,
  attempts: 0,
  maxAttempts: 3,
  resultPath: null,         // Se llena al completar
  rawResult: null,          // JSON string
  errorMessage: null,
  nextRetryAt: null
}
```

**Estados:**
- `PENDING` - Tarea creada, esperando procesamiento
- `PROCESSING` - Worker procesando actualmente
- `RETRYING` - Falló pero se reintentará
- `COMPLETED` - Extracción exitosa
- `FAILED` - Falló después de todos los reintentos
- `CANCELLED` - Cancelada manualmente

### 2. Servicios

#### PdfExtractionService
**Responsabilidad:** Encolar tareas de extracción

**Métodos principales:**
- `enqueueEmailExtractions(ProcessedEmail)` - Crea tareas PENDING
- `retryExtraction(Long taskId)` - Reintentar tarea fallida
- `cancelTask(Long taskId)` - Cancelar tarea
- `getEmailExtractionStats(Long emailId)` - Estadísticas por email

#### ExtractionWorker
**Responsabilidad:** Procesar tareas en background

**Métodos principales:**
- `processExtractionTasks()` - Worker principal [@Scheduled cada 5s]
- `processTask(ExtractionTask)` - Procesa una tarea
- `checkEmailCompletion(ProcessedEmail)` - Verifica si completó
- `recoverStuckTasks()` - Recupera tareas atascadas [@Scheduled cada 1h]

#### WebhookService
**Responsabilidad:** Enviar notificaciones HTTP

**Métodos principales:**
- `sendExtractionCompletedWebhook(email, tasks)` - Webhook cuando completa

**Payload del webhook:**
```json
{
  "event_type": "extraction_completed",
  "timestamp": "2026-01-26T10:00:00Z",
  "email_id": 123,
  "correlation_id": "abc-123-def",
  "sender_email": "proveedor@example.com",
  "subject": "Facturas del mes",
  "tenant_id": 1,
  "tenant_code": "ACME",
  "total_files": 3,
  "extracted_files": 2,
  "failed_files": 1,
  "success_rate": 66.67,
  "extractions": [
    {
      "task_id": 1,
      "original_filename": "factura_001.pdf",
      "normalized_filename": "92455890_123_0001_invoice_jde_2026-01-26.pdf",
      "source": "JDE",
      "status": "COMPLETED",
      "attempts": 1,
      "created_at": "2026-01-26T10:00:00Z",
      "completed_at": "2026-01-26T10:00:15Z",
      "extracted_data": {
        "invoice_number": "INV-2024-001",
        "invoice_date": "2024-01-15",
        "total_amount": 1500.50,
        "vendor_name": "Proveedor XYZ",
        "currency": "USD"
      },
      "result_path": "/private/tmp/ACME/process/extractions/123_1_extraction.json"
    }
  ]
}
```

#### EmailNotificationService
**Responsabilidad:** Enviar notificaciones por email

**Métodos principales:**
- `sendReceivedEmail(ProcessedEmail)` - Email cuando se recibe
- `sendProcessedEmail(email, tasks)` - Email cuando completa extracción

**Templates:**
- `templateEmailReceived` - Notifica recepción (envío inmediato)
- `templateEmailProcessed` - Notifica procesamiento (cuando completan extracciones)

### 3. Repositorios

#### ExtractionTemplateRepository
```java
// Buscar template activo por tenant y source
Optional<ExtractionTemplate> findByTenantIdAndSourceAndIsActive(
    Long tenantId, String source, Boolean isActive
);
```

#### ExtractionTaskRepository
```java
// Buscar tareas listas para procesar
@Query("SELECT t FROM ExtractionTask t WHERE " +
       "(t.status = 'PENDING' OR " +
       " (t.status = 'RETRYING' AND t.nextRetryAt <= :now)) " +
       "ORDER BY t.priority DESC, t.createdAt ASC")
List<ExtractionTask> findNextTasksToProcess(@Param("now") Instant now);

// Verificar si email completó
@Query("SELECT CASE WHEN COUNT(t) = 0 THEN true ELSE false END " +
       "FROM ExtractionTask t WHERE " +
       "t.email.id = :emailId AND " +
       "t.status NOT IN ('COMPLETED', 'FAILED', 'CANCELLED')")
boolean isEmailFullyProcessed(@Param("emailId") Long emailId);
```

---

## ⚙️ Configuración

### 1. application.yml

```yaml
# ============================================
# PDF Extraction Configuration
# ============================================
extraction:
  worker:
    enabled: true                    # Habilitar/deshabilitar worker
    poll-interval-ms: 5000          # Intervalo de polling (5 segundos)
    batch-size: 5                   # Max tareas a procesar por ciclo
    retry-delay-seconds: 60         # Delay base para reintentos
    stuck-task-threshold-minutes: 30 # Umbral para tareas atascadas

  webhook:
    enabled: true                   # Habilitar/deshabilitar webhooks
    timeout-seconds: 30             # Timeout para webhook HTTP
    retry-attempts: 3               # Reintentos para webhooks fallidos
    retry-delay-seconds: 60         # Delay base para reintentos

# ============================================
# Spring Async/Scheduling
# ============================================
spring:
  task:
    scheduling:
      pool:
        size: 3                     # Thread pool para schedulers
    execution:
      pool:
        core-size: 2               # Async execution pool
        max-size: 10
```

### 2. Clase Principal

```java
@SpringBootApplication
@EnableAsync          // ⭐ Habilita @Async
@EnableScheduling     // ⭐ Habilita @Scheduled
public class InvoiceExtractorApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(InvoiceExtractorApiApplication.class, args);
    }
}
```

### 3. Base de Datos

#### Migración Flyway

Ejecutar: `V1.0.2__add_extraction_support.sql`

```sql
-- Agregar campos a tenants
ALTER TABLE tenants
    ADD COLUMN webhook_url VARCHAR(500);

-- Agregar campos a email_sender_rules
ALTER TABLE email_sender_rules
    ADD COLUMN notification_email VARCHAR(255);

-- Crear tabla extraction_templates
CREATE TABLE extraction_templates (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    source VARCHAR(50) NOT NULL,
    template_name VARCHAR(255) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    description VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    UNIQUE KEY uk_tenant_source (tenant_id, source)
);

-- Crear tabla extraction_tasks
CREATE TABLE extraction_tasks (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    processed_attachment_id BIGINT NOT NULL,
    processed_email_id BIGINT NOT NULL,
    pdf_path VARCHAR(500) NOT NULL,
    source VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    priority INT NOT NULL DEFAULT 0,
    result_path VARCHAR(500),
    raw_result TEXT,
    error_message TEXT,
    attempts INT NOT NULL DEFAULT 0,
    max_attempts INT NOT NULL DEFAULT 3,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP NULL,
    completed_at TIMESTAMP NULL,
    next_retry_at TIMESTAMP NULL,

    FOREIGN KEY (processed_attachment_id) REFERENCES processed_attachments(id) ON DELETE CASCADE,
    FOREIGN KEY (processed_email_id) REFERENCES processed_emails(id) ON DELETE CASCADE
);
```

---

## 📊 Modelo de Datos

### Relaciones

```
Tenant
  ├── storageBasePath: "/private/tmp/process-mails"
  ├── templateBasePath: "/config/templates"
  └── webhookUrl: "https://api.cliente.com/webhooks/extraction"

EmailSenderRule
  ├── senderEmail: "proveedor@example.com"
  ├── senderId: "92455890"
  ├── source: "JDE"  (definido en AttachmentProcessingRule)
  ├── templateEmailReceived: "email_recibido.html"
  ├── templateEmailProcessed: "email_procesado.html"
  └── notificationEmail: "admin@empresa.com"

ExtractionTemplate
  ├── tenant_id: 1
  ├── source: "JDE"  ← Debe coincidir con AttachmentProcessingRule.source
  ├── templateName: "jde_invoice_template.json"
  └── fullTemplatePath: {storageBasePath}/{tenantCode}/{templateBasePath}/{templateName}

ProcessedEmail (status=COMPLETED)
  └── ProcessedAttachment (status=DOWNLOADED)
        └── ExtractionTask (status=PENDING → PROCESSING → COMPLETED)
```

### Path de Archivos

```
{tenant.storageBasePath}/
└── {tenant.tenantCode}/
    ├── process/
    │   ├── inbounds/              # PDFs originales
    │   │   └── {senderId}_{emailId}_{seq}_{source}_{dest}_{timestamp}.pdf
    │   └── extractions/           # Resultados JSON
    │       └── {emailId}_{attachmentId}_extraction.json
    └── {tenant.templateBasePath}/  # Templates
        └── {templateName}.json
```

**Ejemplo:**
```
/private/tmp/process-mails/
└── ACME/
    ├── process/
    │   ├── inbounds/
    │   │   └── 92455890_123_0001_invoice_jde_2026-01-26-10-30-00.pdf
    │   └── extractions/
    │       └── 123_1_extraction.json
    └── config/
        └── templates/
            └── jde_invoice_template.json
```

---

## 🚀 API Endpoints

### ExtractionTemplate CRUD

#### Listar todos los templates
```http
GET /api/v1/extraction-templates
Authorization: Bearer {token}
```

#### Listar templates por tenant
```http
GET /api/v1/extraction-templates/tenant/{tenantId}
Authorization: Bearer {token}
```

#### Buscar template por tenant y source
```http
GET /api/v1/extraction-templates/tenant/{tenantId}/source/{source}
Authorization: Bearer {token}
```

#### Crear template
```http
POST /api/v1/extraction-templates
Authorization: Bearer {token}
Content-Type: application/json

{
  "tenantId": 1,
  "source": "JDE",
  "templateName": "jde_invoice_template.json",
  "isActive": true,
  "description": "Template para facturas JDE"
}
```

#### Guardar contenido JSON del template
```http
POST /api/v1/extraction-templates/{id}/save-content
Authorization: Bearer {token}
Content-Type: application/json

{
  "templateContent": {
    "fields": [
      {
        "name": "invoice_number",
        "type": "string",
        "required": true
      },
      {
        "name": "invoice_date",
        "type": "date",
        "required": true
      },
      {
        "name": "total_amount",
        "type": "number",
        "required": true
      },
      {
        "name": "vendor_name",
        "type": "string",
        "required": true
      }
    ]
  },
  "overwrite": false
}
```

#### Actualizar template
```http
PUT /api/v1/extraction-templates/{id}
Authorization: Bearer {token}
Content-Type: application/json

{
  "templateName": "jde_invoice_template_v2.json",
  "description": "Template actualizado"
}
```

#### Activar/Desactivar template
```http
POST /api/v1/extraction-templates/{id}/toggle-status
Authorization: Bearer {token}
```

#### Eliminar template
```http
DELETE /api/v1/extraction-templates/{id}
Authorization: Bearer {token}
```

---

## 📖 Guía de Uso

### Setup Inicial

#### 1. Configurar Tenant
```sql
-- Asegurar que el tenant tiene configurados los paths
UPDATE tenants SET
  storage_base_path = '/private/tmp/process-mails',
  template_base_path = '/config/templates',
  webhook_url = 'https://api.cliente.com/webhooks/extraction'
WHERE id = 1;
```

#### 2. Crear ExtractionTemplate

**Opción A: Via API (recomendado)**

```bash
# 1. Crear el registro del template
curl -X POST http://localhost:8080/api/v1/extraction-templates \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "tenantId": 1,
    "source": "JDE",
    "templateName": "jde_invoice_template.json",
    "isActive": true,
    "description": "Template para facturas JDE"
  }'

# 2. Guardar el contenido JSON del template
curl -X POST http://localhost:8080/api/v1/extraction-templates/1/save-content \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "templateContent": {
      "fields": [
        {"name": "invoice_number", "type": "string", "required": true},
        {"name": "invoice_date", "type": "date", "required": true},
        {"name": "total_amount", "type": "number", "required": true},
        {"name": "vendor_name", "type": "string", "required": true},
        {"name": "currency", "type": "string", "required": false}
      ]
    },
    "overwrite": false
  }'
```

**Opción B: Manualmente**

```bash
# 1. Crear directorio de templates
mkdir -p /private/tmp/process-mails/ACME/config/templates

# 2. Crear archivo template
cat > /private/tmp/process-mails/ACME/config/templates/jde_invoice_template.json <<EOF
{
  "fields": [
    {"name": "invoice_number", "type": "string", "required": true},
    {"name": "invoice_date", "type": "date", "required": true},
    {"name": "total_amount", "type": "number", "required": true},
    {"name": "vendor_name", "type": "string", "required": true},
    {"name": "currency", "type": "string", "required": false}
  ]
}
EOF

# 3. Insertar en BD
INSERT INTO extraction_templates
  (tenant_id, source, template_name, is_active, description)
VALUES
  (1, 'JDE', 'jde_invoice_template.json', true, 'Template para facturas JDE');
```

#### 3. Configurar AttachmentProcessingRule

```sql
-- Asegurar que la regla tenga el source correcto
INSERT INTO attachment_processing_rules
  (sender_rule_id, file_name_regex, source, destination, enabled)
VALUES
  (1, '.*invoice.*\\.pdf', 'JDE', 'invoices', true);
```

**IMPORTANTE:** El `source` de `AttachmentProcessingRule` debe coincidir exactamente con el `source` de `ExtractionTemplate`.

#### 4. Configurar EmailSenderRule (opcional)

```sql
-- Configurar templates de email y notificationEmail
UPDATE email_sender_rules SET
  template_email_received = 'email_recibido.html',
  template_email_processed = 'email_procesado.html',
  notification_email = 'admin@empresa.com',
  auto_reply_enabled = true,
  process_enabled = true
WHERE id = 1;
```

### Verificación

#### 1. Verificar que el worker está corriendo

```bash
# Buscar en logs al iniciar:
grep "ExtractionWorker" logs/application.log

# Deberías ver:
# INFO  ExtractionWorker - Worker enabled: true
# INFO  ExtractionWorker - Poll interval: 5000ms
```

#### 2. Verificar templates configurados

```sql
-- Listar templates activos
SELECT
  et.id,
  t.tenant_code,
  et.source,
  et.template_name,
  CONCAT(t.storage_base_path, '/', t.tenant_code, '/', t.template_base_path, '/', et.template_name) as full_path,
  et.is_active
FROM extraction_templates et
JOIN tenants t ON et.tenant_id = t.id
WHERE et.is_active = true;
```

#### 3. Monitorear tareas de extracción

```sql
-- Ver tareas pendientes
SELECT
  id,
  source,
  status,
  attempts,
  created_at,
  TIMESTAMPDIFF(SECOND, created_at, NOW()) as seconds_waiting
FROM extraction_tasks
WHERE status IN ('PENDING', 'PROCESSING', 'RETRYING')
ORDER BY created_at ASC;

-- Estadísticas generales
SELECT
  status,
  COUNT(*) as count,
  AVG(TIMESTAMPDIFF(SECOND, created_at, completed_at)) as avg_duration_seconds
FROM extraction_tasks
GROUP BY status;
```

### Flujo de Prueba Completo

```bash
# 1. Enviar email de prueba con PDF a la cuenta configurada
# 2. Esperar hasta 60s (intervalo de polling)
# 3. Verificar que se creó ProcessedEmail

SELECT id, from_address, subject, processing_status, total_attachments
FROM processed_emails
ORDER BY created_at DESC LIMIT 1;

# 4. Verificar que se crearon ProcessedAttachments

SELECT id, original_filename, processing_status, file_path
FROM processed_attachments
WHERE processed_email_id = {email_id};

# 5. Verificar que se crearon ExtractionTasks

SELECT id, source, status, attempts, error_message
FROM extraction_tasks
WHERE processed_email_id = {email_id};

# 6. Esperar hasta 5s (intervalo del worker)
# 7. Verificar logs del worker

tail -f logs/application.log | grep "TASK-"

# Deberías ver:
# INFO  [TASK-1] Processing extraction task for PDF: /path/to/pdf
# INFO  [TASK-1] Found template: Template para facturas JDE
# INFO  [TASK-1] Converting PDF to internal format...
# INFO  [TASK-1] Extracting data from PDF...
# INFO  [TASK-1] Extraction result saved to: /path/to/result.json
# INFO  [TASK-1] Extraction completed successfully

# 8. Verificar que la tarea completó

SELECT
  id,
  status,
  result_path,
  completed_at,
  TIMESTAMPDIFF(SECOND, created_at, completed_at) as duration_seconds
FROM extraction_tasks
WHERE id = {task_id};

# 9. Ver el resultado JSON

cat /private/tmp/process-mails/ACME/process/extractions/123_1_extraction.json

# 10. Verificar que se envió el webhook (si configurado)

grep "Webhook sent successfully" logs/application.log
```

---

## 🔧 Troubleshooting

### Problema: Las tareas no se procesan

**Síntomas:**
- Tareas quedan en `PENDING` indefinidamente
- No hay logs de `[TASK-X]`

**Causas posibles:**

1. **Worker deshabilitado**
   ```yaml
   # Verificar en application.yml:
   extraction:
     worker:
       enabled: true  # ← Debe ser true
   ```

2. **@EnableScheduling no configurado**
   ```java
   // Verificar en clase principal:
   @SpringBootApplication
   @EnableScheduling  // ← Debe estar presente
   public class InvoiceExtractorApiApplication { }
   ```

3. **Error en el worker**
   ```bash
   # Buscar errores en logs:
   grep "ERROR.*ExtractionWorker" logs/application.log
   ```

### Problema: Template not found

**Síntomas:**
- Tareas fallan con: "No active template found for tenant=X, source=Y"

**Solución:**

```sql
-- 1. Verificar que existe el template
SELECT * FROM extraction_templates
WHERE tenant_id = 1 AND source = 'JDE' AND is_active = true;

-- 2. Si no existe, crearlo
INSERT INTO extraction_templates
  (tenant_id, source, template_name, is_active, description)
VALUES
  (1, 'JDE', 'jde_invoice_template.json', true, 'Template para facturas JDE');

-- 3. Verificar que el source coincide con la regla
SELECT
  apr.source as rule_source,
  et.source as template_source
FROM attachment_processing_rules apr
LEFT JOIN extraction_templates et
  ON apr.source = et.source
  AND et.tenant_id = 1
WHERE apr.id = 1;
```

### Problema: Template file not found

**Síntomas:**
- Tareas fallan con: "Template file not found: /path/to/template.json"

**Solución:**

```bash
# 1. Verificar el path completo
SELECT
  CONCAT(
    t.storage_base_path, '/',
    t.tenant_code, '/',
    t.template_base_path, '/',
    et.template_name
  ) as full_path
FROM extraction_templates et
JOIN tenants t ON et.tenant_id = t.id
WHERE et.id = 1;

# 2. Verificar que el archivo existe
ls -la /private/tmp/process-mails/ACME/config/templates/

# 3. Si no existe, crearlo via API o manualmente
curl -X POST http://localhost:8080/api/v1/extraction-templates/1/save-content \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -d '{"templateContent": {...}, "overwrite": false}'
```

### Problema: PDF file not found

**Síntomas:**
- Tareas fallan con: "PDF file not found: /path/to/pdf"

**Causas:**

1. **El PDF fue eliminado**
   - Verificar políticas de retención de archivos

2. **Path incorrecto en ProcessedAttachment**
   ```sql
   SELECT id, file_path FROM processed_attachments WHERE id = 1;
   ```

3. **Permisos de lectura**
   ```bash
   ls -la /private/tmp/process-mails/ACME/process/inbounds/
   ```

### Problema: Tareas atascadas en PROCESSING

**Síntomas:**
- Tareas con `status=PROCESSING` por más de 30 minutos

**Solución:**

El worker tiene un recovery automático cada 1 hora (`recoverStuckTasks()`), pero puedes forzarlo:

```sql
-- Ver tareas atascadas
SELECT
  id,
  status,
  started_at,
  TIMESTAMPDIFF(MINUTE, started_at, NOW()) as minutes_processing
FROM extraction_tasks
WHERE status = 'PROCESSING'
  AND started_at < DATE_SUB(NOW(), INTERVAL 30 MINUTE);

-- Marcar para retry manualmente
UPDATE extraction_tasks
SET
  status = 'PENDING',
  started_at = NULL,
  next_retry_at = NULL
WHERE id IN (/* IDs de tareas atascadas */);
```

### Problema: Webhook no se envía

**Síntomas:**
- Tareas completan pero no llega webhook

**Verificaciones:**

```sql
-- 1. Verificar que el tenant tiene webhook URL
SELECT id, tenant_code, webhook_url FROM tenants WHERE id = 1;

-- 2. Verificar que webhook está habilitado
grep "webhook.enabled" application.yml
# Debe ser: enabled: true

-- 3. Buscar errores de webhook en logs
grep "Webhook failed" logs/application.log
```

**Posibles causas:**

1. **URL incorrecta o inaccesible**
   ```bash
   # Probar manualmente
   curl -X POST https://api.cliente.com/webhooks/extraction \
     -H "Content-Type: application/json" \
     -d '{"test": true}'
   ```

2. **Timeout del webhook**
   ```yaml
   # Aumentar timeout en application.yml:
   extraction:
     webhook:
       timeout-seconds: 60  # Aumentar de 30 a 60
   ```

3. **Webhook fallando después de reintentos**
   ```bash
   # Ver en logs:
   grep "Webhook failed after .* attempts" logs/application.log
   ```

### Problema: Email de notificación no se envía

**Síntomas:**
- No llegan emails de recepción o procesamiento

**Verificaciones:**

```sql
-- 1. Verificar configuración de EmailSenderRule
SELECT
  id,
  sender_email,
  notification_email,
  template_email_received,
  template_email_processed,
  auto_reply_enabled,
  process_enabled
FROM email_sender_rules
WHERE id = 1;

-- 2. Verificar que los templates existen
ls -la /path/to/templates/email_*.html
```

**Nota:** Actualmente `EmailNotificationService` tiene una implementación mock. Para envíos reales necesitas:

1. Configurar JavaMailSender
2. Descomentar el código de envío en `sendEmail()`
3. Configurar SMTP en `application.yml`

### Monitoring y Métricas

```sql
-- Dashboard de métricas
SELECT
  -- Tareas por estado
  (SELECT COUNT(*) FROM extraction_tasks WHERE status = 'PENDING') as pending,
  (SELECT COUNT(*) FROM extraction_tasks WHERE status = 'PROCESSING') as processing,
  (SELECT COUNT(*) FROM extraction_tasks WHERE status = 'RETRYING') as retrying,
  (SELECT COUNT(*) FROM extraction_tasks WHERE status = 'COMPLETED') as completed,
  (SELECT COUNT(*) FROM extraction_tasks WHERE status = 'FAILED') as failed,

  -- Tiempo promedio de procesamiento
  (SELECT AVG(TIMESTAMPDIFF(SECOND, created_at, completed_at))
   FROM extraction_tasks
   WHERE status = 'COMPLETED'
   AND completed_at > DATE_SUB(NOW(), INTERVAL 1 HOUR)) as avg_duration_seconds,

  -- Tasa de éxito (última hora)
  (SELECT
    ROUND(100.0 * SUM(CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END) / COUNT(*), 2)
   FROM extraction_tasks
   WHERE created_at > DATE_SUB(NOW(), INTERVAL 1 HOUR)) as success_rate_pct;
```

---

## 📈 Métricas y KPIs

### Métricas Clave

1. **Latencia de Extracción**
   - Tiempo desde que se crea la tarea hasta que completa
   - Objetivo: < 30 segundos por PDF

2. **Tasa de Éxito**
   - % de tareas COMPLETED vs total
   - Objetivo: > 95%

3. **Backlog de Tareas**
   - Número de tareas PENDING/RETRYING
   - Objetivo: < 10

4. **Tiempo de Procesamiento por Email**
   - Desde email recibido hasta webhook enviado
   - Objetivo: < 2 minutos

### Queries Útiles

```sql
-- KPIs del día
SELECT
  DATE(created_at) as date,
  COUNT(*) as total_tasks,
  SUM(CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END) as completed,
  SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END) as failed,
  ROUND(100.0 * SUM(CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END) / COUNT(*), 2) as success_rate,
  AVG(TIMESTAMPDIFF(SECOND, created_at, completed_at)) as avg_duration_sec,
  MAX(TIMESTAMPDIFF(SECOND, created_at, completed_at)) as max_duration_sec
FROM extraction_tasks
WHERE created_at >= CURDATE()
GROUP BY DATE(created_at);

-- Top errores
SELECT
  SUBSTRING(error_message, 1, 100) as error_snippet,
  COUNT(*) as occurrences
FROM extraction_tasks
WHERE status = 'FAILED'
  AND created_at > DATE_SUB(NOW(), INTERVAL 7 DAY)
GROUP BY SUBSTRING(error_message, 1, 100)
ORDER BY occurrences DESC
LIMIT 10;

-- Throughput por hora
SELECT
  DATE_FORMAT(completed_at, '%Y-%m-%d %H:00') as hour,
  COUNT(*) as tasks_completed,
  COUNT(*) / 60.0 as tasks_per_minute
FROM extraction_tasks
WHERE status = 'COMPLETED'
  AND completed_at > DATE_SUB(NOW(), INTERVAL 24 HOUR)
GROUP BY DATE_FORMAT(completed_at, '%Y-%m-%d %H:00')
ORDER BY hour DESC;
```

---

## 🎯 Best Practices

### 1. Templates

- ✅ Versiona tus templates (ej: `jde_invoice_template_v2.json`)
- ✅ Usa nombres descriptivos
- ✅ Documenta los campos en el template JSON
- ✅ Prueba templates antes de activarlos en producción
- ❌ No borres templates activos sin deshabilitarlos primero

### 2. Monitoring

- ✅ Monitorea el backlog de tareas PENDING
- ✅ Alerta si tareas PROCESSING > 30 minutos
- ✅ Revisa logs diariamente
- ✅ Monitorea la tasa de éxito
- ✅ Revisa errores frecuentes

### 3. Performance

- ✅ Ajusta `batch-size` según capacidad del servidor
- ✅ Ajusta `poll-interval-ms` según volumen
- ✅ Considera múltiples instancias para alto volumen (con coordinación)
- ❌ No pongas `poll-interval-ms` < 1000ms (sobrecarga DB)

### 4. Seguridad

- ✅ Usa HTTPS para webhooks
- ✅ Valida certificados SSL
- ✅ Implementa autenticación en webhook endpoint
- ✅ Limita permisos de lectura/escritura de archivos
- ✅ Encripta datos sensibles en templates

### 5. Mantenimiento

- ✅ Limpia tareas antiguas (COMPLETED/FAILED > 30 días)
- ✅ Archiva resultados JSON antiguos
- ✅ Revisa y optimiza índices de BD
- ✅ Mantén logs rotativos (max 7 días)

---

## 📚 Referencias

- **Modelo de Datos:** Ver `ExtractionTemplate.java`, `ExtractionTask.java`
- **Worker Implementation:** Ver `ExtractionWorker.java`
- **API Endpoints:** Ver `ExtractionTemplateController.java`
- **Migration SQL:** Ver `V1.0.2__add_extraction_support.sql`
- **Configuration:** Ver `ExtractionProperties.java`

---

## 🤝 Soporte

Para preguntas o issues:
1. Revisa logs: `logs/application.log`
2. Verifica configuración: `application.yml`
3. Consulta esta documentación
4. Contacta al equipo de desarrollo

---

**Última actualización:** 2026-01-26
**Versión:** 1.0.0
