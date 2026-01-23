# 🚀 FASE 2: Email Polling Service

Sistema automático de polling, descarga y procesamiento de emails y attachments.

---

## 📦 Archivos Creados (12 archivos)

### ✅ Entities (2 archivos)
- `ProcessedEmail.java` - Registro de emails procesados
- `ProcessedAttachment.java` - Registro de attachments descargados

### ✅ Repositories (2 archivos)
- `ProcessedEmailRepository.java` - Queries para emails procesados
- `ProcessedAttachmentRepository.java` - Queries para attachments

### ✅ Services (4 archivos)
- `EmailReaderService.java` - Lee emails desde IMAP/POP3
- `FileStorageService.java` - Almacena archivos (local + S3)
- `EmailProcessingService.java` - Orquesta el procesamiento
- `EmailProcessingHelpers.java` - Helpers (regex matching, metadata)

### ✅ Scheduler (1 archivo)
- `EmailPollingScheduler.java` - Polling automático cada minuto

### ✅ Controller (1 archivo)
- `EmailPollingController.java` - APIs para polling manual y consultas

### ✅ Config (2 archivos)
- `SchedulingConfig.java` - Habilita @Scheduled
- `application-email-polling.properties` - Configuración

---

## 🎯 ¿Cómo Funciona?

### Flujo Completo

```
┌─────────────────────────────────────────────────────────┐
│  1. SCHEDULER (cada 1 minuto)                           │
│     - Busca cuentas con pollingEnabled=true             │
│     - Verifica si pasó el pollingIntervalMinutes        │
└─────────────────────────────────────────────────────────┘
                         ↓
┌─────────────────────────────────────────────────────────┐
│  2. EMAIL READER                                         │
│     - Conecta a IMAP/POP3                               │
│     - Lee emails nuevos (UID > lastProcessedUid)        │
│     - Extrae metadata y attachments                     │
└─────────────────────────────────────────────────────────┘
                         ↓
┌─────────────────────────────────────────────────────────┐
│  3. RULE ENGINE                                          │
│     - Busca sender rule por fromAddress                 │
│     - Si no existe → marcar como IGNORED                │
│     - Aplica regex a cada attachment                    │
└─────────────────────────────────────────────────────────┘
                         ↓
┌─────────────────────────────────────────────────────────┐
│  4. FILE STORAGE                                         │
│     - Guarda attachments que matchearon                 │
│     - Formato: {senderId}_{emailId}_{seq}_{src}_{dst}  │
│     - Path: ATINA/process/inbounds/{filename}          │
└─────────────────────────────────────────────────────────┘
                         ↓
┌─────────────────────────────────────────────────────────┐
│  5. METADATA GENERATOR                                   │
│     - Genera JSON con toda la info del email            │
│     - Guarda en ATINA/process/emails/{metadata}.json   │
└─────────────────────────────────────────────────────────┘
                         ↓
┌─────────────────────────────────────────────────────────┐
│  6. DATABASE UPDATE                                      │
│     - Actualiza lastProcessedUid                        │
│     - Marca email como COMPLETED                        │
└─────────────────────────────────────────────────────────┘
```

---

## 🚀 Instalación

### 1. Copiar archivos

```bash
cd ATINA-PDF-Processer-API/src/main/java/com/atina/invoice/api

# Entities
cp /path/to/entities/ProcessedEmail.java model/
cp /path/to/entities/ProcessedAttachment.java model/

# Repositories
cp /path/to/repositories/ProcessedEmailRepository.java repository/
cp /path/to/repositories/ProcessedAttachmentRepository.java repository/

# Services
cp /path/to/services/EmailReaderService.java service/
cp /path/to/services/FileStorageService.java service/
cp /path/to/services/EmailProcessingService.java service/
cp /path/to/services/EmailProcessingHelpers.java service/

# Scheduler
mkdir -p scheduler
cp /path/to/scheduler/EmailPollingScheduler.java scheduler/

# Controller
cp /path/to/controllers/EmailPollingController.java controller/

# Config
cp /path/to/config/SchedulingConfig.java config/
```

### 2. Agregar configuración

En `application.properties`:

```properties
# Email polling
email.polling.enabled=true
storage.default.type=LOCAL
storage.default.base-path=/private/tmp/process-mails
```

O copiar el archivo completo:

```bash
cp /path/to/config/application-email-polling.properties \
   src/main/resources/
```

### 3. Actualizar Tenant.java (si no lo hiciste en FASE 1)

```java
@Enumerated(EnumType.STRING)
@Column(name = "storage_type")
@Builder.Default
private StorageType storageType = StorageType.LOCAL;

@Column(name = "storage_base_path", length = 500)
@Builder.Default
private String storageBasePath = "/private/tmp/process-mails";
```

### 4. Compilar y ejecutar

```bash
# Compilar
mvn clean compile -DskipTests

# Ejecutar
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=dev"
```

**Hibernate creará automáticamente:**
- `processed_emails` (26 columnas)
- `processed_attachments` (13 columnas)

---

## 📊 Nuevas APIs Creadas (4 endpoints)

### 1. Polling Manual

```http
POST /api/v1/email-polling/poll-now/{emailAccountId}
Authorization: Bearer <token>

Response:
{
  "success": true,
  "data": {
    "email_account_id": 1,
    "emails_processed": 3,
    "message": "Successfully processed 3 emails"
  }
}
```

### 2. Listar Emails Procesados

```http
GET /api/v1/email-polling/processed-emails?page=0&size=20&sortBy=processedDate&sortDir=DESC
Authorization: Bearer <token>

Response:
{
  "success": true,
  "data": {
    "content": [
      {
        "id": 1,
        "emailUid": "12345",
        "subject": "Invoice for September",
        "fromAddress": "fjgodino@gmail.com",
        "processingStatus": "COMPLETED",
        "totalAttachments": 2,
        "processedAttachments": 2,
        "processedDate": "2026-01-19T15:30:00Z"
      }
    ],
    "totalElements": 100,
    "totalPages": 5,
    "number": 0
  }
}
```

### 3. Ver Detalle de Email Procesado

```http
GET /api/v1/email-polling/processed-emails/1
Authorization: Bearer <token>

Response:
{
  "success": true,
  "data": {
    "id": 1,
    "emailUid": "12345",
    "subject": "Invoice for September",
    "fromAddress": "fjgodino@gmail.com",
    "attachments": [
      {
        "id": 1,
        "originalFilename": "Invoice123.pdf",
        "normalizedFilename": "92455890_1_0001_invoice_jde_2026-01-19-15-30-00.pdf",
        "filePath": "/private/tmp/process-mails/ACME/process/inbounds/...",
        "fileSizeBytes": 245678,
        "processingStatus": "DOWNLOADED",
        "rule": {
          "fileNameRegex": "^Invoice+([0-9])+(.PDF|.pdf)$",
          "source": "invoice",
          "destination": "jde"
        }
      }
    ],
    "rawMetadata": { ... }
  }
}
```

### 4. Estadísticas de Procesamiento

```http
GET /api/v1/email-polling/stats
Authorization: Bearer <token>

Response:
{
  "success": true,
  "data": {
    "total_completed": 150,
    "pending": 2,
    "failed": 3,
    "ignored": 20,
    "processed_today": 15,
    "tenant_id": 1
  }
}
```

---

## 🧪 Probar el Sistema

### Paso 1: Crear Email Account (FASE 1)

```bash
POST http://localhost:8080/api/v1/email-accounts
{
  "emailAddress": "test@gmail.com",
  "emailType": "IMAP",
  "host": "imap.gmail.com",
  "port": 993,
  "username": "test@gmail.com",
  "password": "app-password",
  "useSsl": true,
  "pollingEnabled": true,
  "pollingIntervalMinutes": 5,
  "folderName": "INBOX"
}
```

### Paso 2: Importar Sender Rule (FASE 1)

```bash
POST http://localhost:8080/api/v1/sender-rules/import-json?emailAccountId=1
{
  "email": "fjgodino@gmail.com",
  "id": "92455890",
  "templates": {
    "email-received": "reply-mail-received.html",
    "email-processed": "reply-mail-processed.html"
  },
  "rules": [
    {
      "id": 1,
      "fileRule": "^Invoice+([0-9])+(.PDF|.pdf)$",
      "source": "invoice",
      "destination": "jde",
      "metodo": ""
    }
  ]
}
```

### Paso 3: Esperar polling automático (5 minutos)

O forzar polling manual:

```bash
POST http://localhost:8080/api/v1/email-polling/poll-now/1
```

### Paso 4: Ver emails procesados

```bash
GET http://localhost:8080/api/v1/email-polling/processed-emails
```

### Paso 5: Verificar archivos descargados

```bash
ls -la /private/tmp/process-mails/ACME/process/inbounds/
```

Deberías ver archivos con nombres como:
```
92455890_1_0001_invoice_jde_2026-01-19-15-30-00.pdf
92455890_1_0002_invoice_jde_2026-01-19-15-30-01.pdf
```

### Paso 6: Ver metadata JSON

```bash
cat /private/tmp/process-mails/ACME/process/emails/92455890_1_fjgodino@gmail.com.json
```

Deberías ver:

```json
{
  "email_id": 1,
  "email_uid": "12345",
  "subject": "Invoice for September",
  "from": "fjgodino@gmail.com",
  "processed_date": "2026-01-19T15:30:00Z",
  "sender_rule": {
    "sender_id": "92455890",
    "sender_name": "Fernando Godino",
    "sender_email": "fjgodino@gmail.com"
  },
  "attachments": [
    {
      "sequence": 1,
      "original_filename": "Invoice123.pdf",
      "normalized_filename": "92455890_1_0001_invoice_jde_2026-01-19-15-30-00.pdf",
      "file_path": "/private/tmp/process-mails/ACME/process/inbounds/...",
      "file_size": 245678,
      "rule": {
        "regex": "^Invoice+([0-9])+(.PDF|.pdf)$",
        "source": "invoice",
        "destination": "jde"
      }
    }
  ]
}
```

---

## ⚙️ Configuración Avanzada

### Cambiar intervalo de polling del scheduler

Por defecto el scheduler se ejecuta cada 1 minuto. Para cambiarlo:

```properties
# En application.properties
email.polling.scheduler.fixed-rate=120000  # 2 minutos
```

**Nota:** Aunque el scheduler se ejecute cada minuto, solo procesará cuentas que hayan alcanzado su `pollingIntervalMinutes`.

### Configurar polling por cuenta

Cada email account tiene su propio `pollingIntervalMinutes`:

```bash
# Gmail: cada 5 minutos
PUT /api/v1/email-accounts/1
{
  "pollingIntervalMinutes": 5
}

# Outlook: cada 10 minutos
PUT /api/v1/email-accounts/2
{
  "pollingIntervalMinutes": 10
}
```

### Habilitar/Deshabilitar polling

#### Por cuenta:

```bash
PATCH /api/v1/email-accounts/1/toggle-polling?enabled=false
```

#### Globalmente:

```properties
# En application.properties
email.polling.enabled=false
```

---

## 📁 Estructura de Archivos

### Attachments descargados

```
{basePath}/{TENANT_CODE}/process/inbounds/
└── {senderId}_{emailId}_{sequence}_{source}_{destination}_{timestamp}.ext

Ejemplo:
/private/tmp/process-mails/ACME/process/inbounds/
└── 92455890_1_0001_invoice_jde_2026-01-19-15-30-00.pdf
```

### Metadata JSON

```
{basePath}/{TENANT_CODE}/process/emails/
└── {senderId}_{emailId}_{senderEmail}.json

Ejemplo:
/private/tmp/process-mails/ACME/process/emails/
└── 92455890_1_fjgodino@gmail.com.json
```

---

## 🔍 Monitoreo y Logs

### Ver logs del scheduler

```bash
tail -f logs/application.log | grep EmailPollingScheduler
```

**Ejemplo de logs:**

```
2026-01-19 15:30:00 [EmailPollingScheduler] 🔄 Running email polling scheduler...
2026-01-19 15:30:00 [EmailPollingScheduler] 📋 Found 2 email accounts enabled for polling
2026-01-19 15:30:00 [EmailPollingScheduler] 🔍 Polling emails from: test@gmail.com
2026-01-19 15:30:05 [EmailProcessingService] 📧 Found 3 new emails in test@gmail.com
2026-01-19 15:30:05 [EmailProcessingService] 📨 Processing email from fjgodino@gmail.com: Invoice for September
2026-01-19 15:30:06 [EmailProcessingService] 📎 Processing 2 attachments
2026-01-19 15:30:07 [FileStorageService] ✅ Saved: Invoice123.pdf → 92455890_1_0001_invoice_jde_2026-01-19-15-30-00.pdf
2026-01-19 15:30:08 [EmailProcessingService] ✅ Email 12345 processed: 2 attachments
2026-01-19 15:30:10 [EmailPollingScheduler] 🎉 Polling completed: 1 accounts processed, 3 emails total
```

### Ver estadísticas en tiempo real

```bash
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/v1/email-polling/stats
```

---

## 🐛 Troubleshooting

### Problema: El scheduler no se ejecuta

**Verificar:**

```properties
# En application.properties
email.polling.enabled=true
```

**Verificar logs:**

```bash
grep "EmailPollingScheduler" logs/application.log
```

### Problema: No se procesan emails

**Posibles causas:**

1. **Polling deshabilitado en la cuenta:**

```sql
SELECT polling_enabled, polling_interval_minutes, last_poll_date
FROM email_accounts WHERE id = 1;
```

2. **No han pasado los minutos suficientes:**

```bash
# Si pollingIntervalMinutes = 10, debe esperar 10 minutos
# desde last_poll_date
```

3. **No hay sender rule configurada:**

```sql
SELECT * FROM email_sender_rules 
WHERE email_account_id = 1 AND sender_email = 'sender@example.com';
```

### Problema: Attachments no se descargan

**Verificar que matchean el regex:**

```bash
POST /api/v1/attachment-rules/test-regex?regex=^Invoice+([0-9])+(.PDF|.pdf)$
["Invoice123.pdf", "Report.pdf"]
```

**Verificar permisos del directorio:**

```bash
ls -la /private/tmp/process-mails/
# Debe ser writable
```

### Problema: Error de conexión IMAP/POP3

**Test de conexión:**

```bash
POST /api/v1/email-accounts/1/test-connection
```

**Para Gmail:** Usar App Password, no la contraseña normal

---

## 📊 Resumen FASE 2

**✅ Archivos creados:** 12 archivos  
**✅ Nuevas tablas:** 2 (processed_emails, processed_attachments)  
**✅ Nuevos endpoints:** 4 APIs  
**✅ Scheduler:** Polling automático cada minuto  
**✅ Features:**
- ✅ Lectura automática de emails (IMAP/POP3)
- ✅ Aplicación de reglas de sender
- ✅ Matching de attachments con regex
- ✅ Descarga y normalización de archivos
- ✅ Generación de metadata JSON
- ✅ Tracking completo de procesamiento
- ✅ Polling manual y automático
- ✅ Estadísticas de procesamiento

---

## 🎯 Próximos Pasos

### FASE 3: Integration con API de Extracción

**Crear:**
- Cliente HTTP para llamar a tu API de extracción
- Job tracking
- Retry logic
- Estado de extracción

### FASE 4: Notifications

**Crear:**
- Email sender service
- Template engine (Thymeleaf)
- ActiveMQ producer
- Notificaciones de "email recibido" y "email procesado"

---

**¿Listo para FASE 3?** 🚀
