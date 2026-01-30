# Sistema de Notificaciones

## Descripcion General

El sistema de notificaciones permite configurar reglas a nivel de **Tenant** para enviar notificaciones automaticas a traves de multiples canales (Email, Slack, API Webhook) cuando ocurren eventos clave en el procesamiento de emails y PDFs.

---

## Conceptos Clave

### Eventos (`NotificationEvent`)

| Evento | Cuando se dispara | Contexto disponible |
|--------|-------------------|---------------------|
| `EMAIL_RECEIVED` | Se recibe un email y se descargan los PDFs | email, attachments, tenant, senderRule |
| `EXTRACTION_COMPLETED` | Todos los PDFs de un email terminan de procesarse | email, tasks, tenant, senderRule |
| `WEBHOOK_CALLBACK` | El tenant confirma procesamiento via callback | email, tasks, callbackResponse, tenant |

### Canales (`NotificationChannel`)

| Canal | Descripcion | Requiere en `channel_config` |
|-------|-------------|------------------------------|
| `EMAIL` | Envia email HTML usando templates Mustache | `{"email": "admin@acme.com"}` (para TENANT_USER) |
| `SLACK` | Envia mensaje a un canal Slack via webhook | `{"webhook_url": "https://hooks.slack.com/services/..."}` |
| `API_WEBHOOK` | HTTP POST con payload JSON a un endpoint | `{"url": "https://...", "headers": {"X-Api-Key": "abc"}}` |

### Tipo de Destinatario (`NotificationRecipientType`)

| Tipo | Descripcion |
|------|-------------|
| `SENDER` | El emisor del email original (`fromAddress` dinamico) |
| `TENANT_USER` | Un usuario fijo del tenant (configurado en `channel_config`) |

---

## Paso 1: Migracion de Base de Datos

La migracion Flyway `V1.0.5__add_notification_rules.sql` crea las tablas automaticamente al iniciar la aplicacion.

Si usas `ddl-auto: update` en desarrollo, Hibernate tambien las crea automaticamente.

Las tablas creadas son:

- `tenant_notification_rules` - Reglas de notificacion por tenant
- `webhook_callback_responses` - Respuestas de callback recibidas

---

## Paso 2: Crear Reglas de Notificacion

### API CRUD

```
GET    /api/v1/tenants/{tenantId}/notification-rules
POST   /api/v1/tenants/{tenantId}/notification-rules
PUT    /api/v1/tenants/{tenantId}/notification-rules/{ruleId}
DELETE /api/v1/tenants/{tenantId}/notification-rules/{ruleId}
```

### Ejemplos de Creacion

#### Email al emisor cuando se recibe un email

```bash
curl -X POST http://localhost:8080/api/v1/tenants/1/notification-rules \
  -H "Content-Type: application/json" \
  -u admin:admin123 \
  -d '{
    "event": "EMAIL_RECEIVED",
    "recipient_type": "SENDER",
    "channel": "EMAIL",
    "channel_config": "{}",
    "template_name": "received-notification.mustache",
    "subject_template": "Confirmacion de Recepcion - Documentos Recibidos",
    "enabled": true
  }'
```

#### Email al admin del tenant cuando se completa la extraccion

```bash
curl -X POST http://localhost:8080/api/v1/tenants/1/notification-rules \
  -H "Content-Type: application/json" \
  -u admin:admin123 \
  -d '{
    "event": "EXTRACTION_COMPLETED",
    "recipient_type": "TENANT_USER",
    "channel": "EMAIL",
    "channel_config": "{\"email\": \"admin@acme.com\"}",
    "template_name": "processed-notification.mustache",
    "subject_template": "Procesamiento Completo - Resultados de Extraccion",
    "enabled": true
  }'
```

#### Slack cuando se completa la extraccion

```bash
curl -X POST http://localhost:8080/api/v1/tenants/1/notification-rules \
  -H "Content-Type: application/json" \
  -u admin:admin123 \
  -d '{
    "event": "EXTRACTION_COMPLETED",
    "recipient_type": "TENANT_USER",
    "channel": "SLACK",
    "channel_config": "{\"webhook_url\": \"https://hooks.slack.com/services/T.../B.../xxx\"}",
    "enabled": true
  }'
```

#### API Webhook cuando se recibe un email

```bash
curl -X POST http://localhost:8080/api/v1/tenants/1/notification-rules \
  -H "Content-Type: application/json" \
  -u admin:admin123 \
  -d '{
    "event": "EMAIL_RECEIVED",
    "recipient_type": "TENANT_USER",
    "channel": "API_WEBHOOK",
    "channel_config": "{\"url\": \"https://vendor-api.com/notify\", \"headers\": {\"X-Api-Key\": \"abc123\"}}",
    "enabled": true
  }'
```

#### Email al emisor cuando el tenant confirma via callback

```bash
curl -X POST http://localhost:8080/api/v1/tenants/1/notification-rules \
  -H "Content-Type: application/json" \
  -u admin:admin123 \
  -d '{
    "event": "WEBHOOK_CALLBACK",
    "recipient_type": "SENDER",
    "channel": "EMAIL",
    "channel_config": "{}",
    "template_name": "callback-confirmation.mustache",
    "subject_template": "Su documento fue procesado - {{callbackReference}}",
    "enabled": true
  }'
```

---

## Paso 3: Templates de Email (canal EMAIL)

Los templates usan **Mustache** y se almacenan en:

```
{storageBasePath}/{tenantCode}/config/email-templates/{templateName}
```

### Variables disponibles por evento

#### EMAIL_RECEIVED

| Variable | Descripcion |
|----------|-------------|
| `emailId` | ID del email |
| `correlationId` | ID de correlacion |
| `subject` | Asunto del email |
| `fromAddress` | Email del remitente |
| `receivedAt` | Fecha de recepcion formateada |
| `tenantName` | Nombre del tenant |
| `tenantCode` | Codigo del tenant |
| `totalAttachments` | Total de archivos adjuntos |
| `processedAttachments` | Adjuntos procesados |
| `hasRejected` | `true` si hay adjuntos rechazados |
| `attachments` | Lista con `filename` y `size bytes` |

#### EXTRACTION_COMPLETED

| Variable | Descripcion |
|----------|-------------|
| `emailId` | ID del email |
| `correlationId` | ID de correlacion |
| `subject` | Asunto del email |
| `fromAddress` | Email del remitente |
| `tenantName` / `tenantCode` | Info del tenant |
| `totalFiles` | Total de archivos procesados |
| `extractedFiles` | Exitosos |
| `failedFiles` | Fallidos |
| `successRate` | Porcentaje de exito |
| `isFullSuccess` | `true` si todos exitosos |
| `hasFailures` / `hasSuccesses` | Flags |
| `extractions` | Lista con detalle por archivo |

Cada item en `extractions`:

| Variable | Descripcion |
|----------|-------------|
| `filename` | Nombre original del archivo |
| `status` | COMPLETED / FAILED |
| `success` | boolean |
| `resultPath` | Ruta del resultado (si exitoso) |
| `hasInvoiceData` | `true` si se extrajeron datos de factura |
| `invoiceNumber`, `invoiceDate`, `total`, `currency`, `vendor` | Datos extraidos |
| `errorMessage` | Mensaje de error (si fallo) |

#### WEBHOOK_CALLBACK

Incluye las variables de `EXTRACTION_COMPLETED` mas:

| Variable | Descripcion |
|----------|-------------|
| `callbackStatus` | Estado del callback (PROCESSED, REJECTED, ERROR) |
| `callbackReference` | Referencia del sistema del tenant (ej: OC-2026-001234) |
| `callbackMessage` | Mensaje opcional del tenant |
| `callbackReceivedAt` | Fecha de recepcion del callback |

### Ejemplo de template

```html
<html>
<body>
  <h2>Documentos Recibidos</h2>
  <p>Hemos recibido su email "<strong>{{subject}}</strong>".</p>
  <p>Se descargaron {{processedAttachments}} de {{totalAttachments}} adjuntos.</p>

  <h3>Archivos recibidos:</h3>
  <ul>
    {{#attachments}}
    <li>{{filename}} ({{size bytes}})</li>
    {{/attachments}}
  </ul>

  <p>Correlation ID: {{correlationId}}</p>
</body>
</html>
```

---

## Paso 4: Configurar Slack (canal SLACK)

1. Ir a https://api.slack.com/apps y crear una app (o usar una existente)
2. Activar **Incoming Webhooks**
3. Crear un webhook para el canal deseado
4. Copiar la URL del webhook (formato: `https://hooks.slack.com/services/T.../B.../xxx`)
5. Usar esa URL en el `channel_config` de la regla

El mensaje enviado a Slack es texto plano con formato Markdown de Slack:

```
*Extraction Completed*
From: proveedor@ejemplo.com
Subject: Factura Enero 2026
Results: 3 completed, 0 failed / 3 total
Correlation ID: `a1b2c3d4-...`
```

---

## Paso 5: Configurar API Webhook (canal API_WEBHOOK)

Configurar `channel_config` con:

```json
{
  "url": "https://tu-api.com/webhook/notifications",
  "headers": {
    "Authorization": "Bearer tu-token",
    "X-Custom-Header": "valor"
  }
}
```

El sistema envia un `POST` con `Content-Type: application/json`. El payload varia segun el evento:

### Payload EMAIL_RECEIVED

```json
{
  "event": "EMAIL_RECEIVED",
  "timestamp": "2026-01-30T10:00:00Z",
  "correlation_id": "uuid-del-email",
  "sender_email": "proveedor@ejemplo.com",
  "subject": "Factura Enero 2026",
  "tenant_code": "ACME",
  "total_attachments": 3,
  "processed_attachments": 3,
  "attachments": [
    { "filename": "factura.pdf", "status": "DOWNLOADED" }
  ]
}
```

### Payload EXTRACTION_COMPLETED

```json
{
  "event": "EXTRACTION_COMPLETED",
  "timestamp": "2026-01-30T10:05:00Z",
  "correlation_id": "uuid-del-email",
  "sender_email": "proveedor@ejemplo.com",
  "subject": "Factura Enero 2026",
  "tenant_code": "ACME",
  "total_files": 3,
  "extracted_files": 3,
  "failed_files": 0,
  "success_rate": 100.0,
  "extractions": [
    {
      "task_id": 1,
      "correlation_id": "uuid-de-la-tarea",
      "original_filename": "factura.pdf",
      "source": "invoice",
      "status": "COMPLETED",
      "extracted_data": { "..." : "..." }
    }
  ]
}
```

### Payload WEBHOOK_CALLBACK

```json
{
  "event": "WEBHOOK_CALLBACK",
  "timestamp": "2026-01-30T10:10:00Z",
  "correlation_id": "uuid-del-email",
  "sender_email": "proveedor@ejemplo.com",
  "tenant_code": "ACME",
  "callback_status": "PROCESSED",
  "callback_reference": "OC-2026-001234",
  "callback_message": "Factura registrada exitosamente"
}
```

---

## Paso 6: Webhook Callback (opcional)

El endpoint de callback permite que el sistema del tenant confirme que proceso la informacion. Esto dispara el evento `WEBHOOK_CALLBACK` que puede notificar al emisor original.

### Endpoint

```
POST /api/v1/webhook-callback
```

### Request

```bash
curl -X POST http://localhost:8080/api/v1/webhook-callback \
  -H "Content-Type: application/json" \
  -d '{
    "correlation_id": "uuid-del-email",
    "status": "PROCESSED",
    "reference": "OC-2026-001234",
    "message": "Factura registrada exitosamente"
  }'
```

### Campos

| Campo | Requerido | Descripcion |
|-------|-----------|-------------|
| `correlation_id` | Si | El `correlationId` del email procesado |
| `status` | Si | Estado: `PROCESSED`, `REJECTED`, `ERROR` |
| `reference` | No | Referencia del sistema del tenant |
| `message` | No | Mensaje descriptivo |

### Respuesta exitosa (200)

```json
{
  "message": "Callback received",
  "correlation_id": "uuid-del-email"
}
```

### Errores

| Codigo | Motivo |
|--------|--------|
| 400 | Falta `correlation_id` o `status` |
| 404 | No se encontro email con ese `correlation_id` |

---

## Flujo Completo (Ejemplo)

```
1. Proveedor envia email con facturas PDF
        |
2. Sistema recibe email, descarga PDFs
        |
3. Dispara EMAIL_RECEIVED
        |--- Regla EMAIL/SENDER --> Email al proveedor: "Recibimos tus documentos"
        |--- Regla SLACK/TENANT_USER --> Slack: "Nuevo email de proveedor@..."
        |--- Regla API_WEBHOOK/TENANT_USER --> POST al ERP del tenant
        |
4. PDFs se procesan (extraccion)
        |
5. Todos terminan --> Dispara EXTRACTION_COMPLETED
        |--- Regla EMAIL/TENANT_USER --> Email al admin: "Extraccion completa"
        |--- Regla SLACK/TENANT_USER --> Slack: "3/3 extracciones exitosas"
        |
6. ERP del tenant procesa y confirma via callback
        |
7. POST /api/v1/webhook-callback --> Dispara WEBHOOK_CALLBACK
        |--- Regla EMAIL/SENDER --> Email al proveedor: "Su factura fue registrada como OC-2026-001234"
```

---

## Restriccion de Unicidad

Cada combinacion `(tenant_id, event, recipient_type, channel)` debe ser unica. No se pueden crear dos reglas identicas para el mismo tenant, evento, tipo de destinatario y canal.

---

## Habilitar / Deshabilitar Reglas

Para deshabilitar una regla sin eliminarla:

```bash
curl -X PUT http://localhost:8080/api/v1/tenants/1/notification-rules/5 \
  -H "Content-Type: application/json" \
  -u admin:admin123 \
  -d '{"enabled": false}'
```

Las reglas deshabilitadas no se ejecutan pero se mantienen en la base de datos.
