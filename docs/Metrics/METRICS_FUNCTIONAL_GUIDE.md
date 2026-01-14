# 📊 Sistema de Métricas - Guía Funcional Completa

## 🎯 ¿Qué Son Las Métricas?

Las métricas son **contadores** que registran actividad del sistema. Cada vez que algo importante sucede (extracción de PDF, login, error), se incrementa un contador.

### Estructura de una Métrica

```
┌─────────────────────────────────────────┐
│ Tenant: ACME                            │
│ Métrica Key: "api.calls.extract"       │
│ Valor: 150                              │
│ Fecha: 2026-01-14                       │
└─────────────────────────────────────────┘
```

---

## 📝 ¿Qué Métricas Se Registran?

### 1. API Calls Generales

| Métrica Key | Cuándo Se Incrementa | Qué Registra |
|-------------|---------------------|--------------|
| `api.calls.total` | En **cada** llamada a la API | Total de requests al sistema |
| `api.calls.extract` | Al extraer un PDF | Total extracciones |
| `api.calls.extract.success` | Extracción exitosa | Extracciones correctas |
| `api.calls.extract.failure` | Extracción fallida | Extracciones con error |
| `api.calls.validate` | Al validar datos | Total validaciones |
| `api.calls.batch` | Al procesar batch | Total operaciones batch |
| `api.calls.batch.items` | Items en batch | Cantidad de items procesados |

### 2. Métricas de AI

| Métrica Key | Cuándo Se Incrementa | Qué Registra |
|-------------|---------------------|--------------|
| `api.calls.ai.generate` | Al usar AI para generar | Uso de AI generativa |

### 3. Métricas de Errores

| Métrica Key | Cuándo Se Incrementa | Qué Registra |
|-------------|---------------------|--------------|
| `errors.total` | En cualquier error | Total de errores |
| `errors.validation` | Error de validación | Errores de datos |
| `errors.processing` | Error de procesamiento | Errores internos |

---

## 📊 Cómo Se Registran Automáticamente

### Ejemplo: Extracción de PDF

```java
// En ExtractionService.java

public ExtractionResult extractPdf(File pdf) {
    try {
        // ... lógica de extracción ...
        
        // ✅ Éxito: registra métricas automáticamente
        metricsService.recordExtractionSuccess();
        // Esto incrementa:
        // - api.calls.extract.success
        // - api.calls.extract
        // - api.calls.total
        
        return result;
        
    } catch (Exception e) {
        // ❌ Error: registra fallo
        metricsService.recordExtractionFailure();
        // Esto incrementa:
        // - api.calls.extract.failure
        // - api.calls.extract
        // - api.calls.total
        
        throw e;
    }
}
```

**El usuario no hace nada** - Las métricas se registran automáticamente en segundo plano.

---

## 🔍 APIs de Métricas - Nivel Usuario/Tenant

### Endpoint: GET /api/v1/metrics

**Quién puede acceder:** Usuarios y admins del tenant  
**Qué hace:** Muestra métricas del propio tenant  
**Para qué sirve:** Ver uso de tu cuenta

#### Request
```bash
curl -X GET http://localhost:8080/api/v1/metrics \
  -H "Authorization: Bearer $TOKEN"
```

#### Response
```json
{
  "success": true,
  "data": {
    "api.calls.total": 450,
    "api.calls.extract": 380,
    "api.calls.extract.success": 360,
    "api.calls.extract.failure": 20,
    "api.calls.validate": 50,
    "api.calls.ai.generate": 20
  }
}
```

#### Casos de Uso

**1. Dashboard de Usuario**
```javascript
// En tu frontend
const metrics = await getMetrics();

// Mostrar:
// - Total de PDFs procesados: 380
// - Tasa de éxito: 360/380 = 94.7%
// - Uso de AI: 20 generaciones
```

**2. Verificar Quota**
```javascript
const quota = user.tenant.maxApiCallsPerMonth; // 1,000,000
const used = metrics["api.calls.total"];        // 450
const remaining = quota - used;                 // 999,550

// Mostrar: "Has usado 450 de 1,000,000 llamadas este mes (0.045%)"
```

**3. Alertas de Límite**
```javascript
if (used > quota * 0.8) {
  alert("⚠️ Has usado el 80% de tu quota mensual");
}
```

---

## 🔍 APIs de Métricas - Nivel Super Admin

### 1. Ver Todos los Tenants

**GET** `/api/v1/admin/metrics/tenants`

**Qué hace:** Lista TODOS los tenants con sus métricas  
**Para qué sirve:** Dashboard global del sistema

#### Request
```bash
curl -X GET http://localhost:8080/api/v1/admin/metrics/tenants \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

#### Response
```json
{
  "success": true,
  "data": {
    "ACME": {
      "id": 2,
      "code": "ACME",
      "name": "ACME Corporation",
      "subscriptionTier": "PREMIUM",
      "metrics": {
        "api.calls.total": 45230,
        "api.calls.extract": 38500,
        "api.calls.extract.success": 38100,
        "api.calls.extract.failure": 400
      },
      "totalApiCalls": 45230,
      "quotaUsagePercent": 4.52
    },
    "GLOBEX": {
      "id": 3,
      "code": "GLOBEX",
      "name": "Globex Corporation",
      "subscriptionTier": "BASIC",
      "metrics": {
        "api.calls.total": 7850
      },
      "totalApiCalls": 7850,
      "quotaUsagePercent": 7.85
    }
  }
}
```

#### Casos de Uso

**1. Dashboard de Admin del Sistema**
```javascript
// Mostrar tabla:
// | Tenant | Tier    | API Calls | Quota Usage | Status |
// |--------|---------|-----------|-------------|--------|
// | ACME   | PREMIUM | 45,230    | 4.5%        | ✅     |
// | GLOBEX | BASIC   | 7,850     | 7.9%        | ✅     |
// | CLIENT | FREE    | 9,800     | 98%         | ⚠️     |
```

**2. Alertas de Quota**
```javascript
tenants.forEach(tenant => {
  if (tenant.quotaUsagePercent > 90) {
    sendAlert(`Tenant ${tenant.code} ha usado el ${tenant.quotaUsagePercent}% de su quota`);
  }
});
```

**3. Reportes de Facturación**
```javascript
// Calcular facturación por uso
tenants
  .filter(t => t.subscriptionTier === "PAY_PER_USE")
  .forEach(tenant => {
    const cost = tenant.totalApiCalls * 0.01; // $0.01 por llamada
    console.log(`${tenant.code}: $${cost}`);
  });
```

---

### 2. Ver Tenant Específico

**GET** `/api/v1/admin/metrics/tenants/{id}`

**Qué hace:** Detalle profundo de un tenant  
**Para qué sirve:** Analizar cliente específico

#### Request
```bash
curl -X GET http://localhost:8080/api/v1/admin/metrics/tenants/2 \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

#### Response
```json
{
  "success": true,
  "data": {
    "tenant": {
      "id": 2,
      "code": "ACME",
      "name": "ACME Corporation",
      "tier": "PREMIUM",
      "maxApiCalls": 1000000
    },
    "metrics": {
      "api.calls.total": 45230,
      "api.calls.extract": 38500,
      "api.calls.extract.success": 38100,
      "api.calls.extract.failure": 400,
      "api.calls.validate": 5000,
      "api.calls.ai.generate": 1730,
      "errors.total": 420,
      "errors.validation": 20,
      "errors.processing": 400
    },
    "usage": {
      "totalApiCalls": 45230,
      "quotaUsagePercent": 4.52,
      "callsRemaining": 954770,
      "successRate": 98.96,
      "errorRate": 1.04
    }
  }
}
```

#### Casos de Uso

**1. Análisis de Cliente**
```javascript
// Support puede ver:
// - Tasa de éxito: 98.96% (muy buena)
// - Tasa de error: 1.04% (normal)
// - Uso: 4.5% de quota (saludable)
// - Errores: 400 de procesamiento, 20 de validación
```

**2. Optimización de Plan**
```javascript
if (tenant.quotaUsagePercent < 10) {
  suggestDowngrade("Este tenant usa solo el 4.5%, podría bajar a BASIC");
}

if (tenant.quotaUsagePercent > 80) {
  suggestUpgrade("Este tenant usa el 85%, debería subir a plan mayor");
}
```

**3. Soporte Técnico**
```javascript
// Cliente reporta problemas
const errorRate = metrics["api.calls.extract.failure"] / metrics["api.calls.extract"];

if (errorRate > 0.05) { // >5% de errores
  console.log("Cliente tiene tasa de error alta - revisar logs");
}
```

---

### 3. Top Tenants por Uso

**GET** `/api/v1/admin/metrics/top-tenants?limit=10`

**Qué hace:** Lista los N tenants que más usan el sistema  
**Para qué sirve:** Identificar clientes principales

#### Request
```bash
curl -X GET "http://localhost:8080/api/v1/admin/metrics/top-tenants?limit=10" \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

#### Response
```json
{
  "success": true,
  "data": {
    "limit": 10,
    "topTenants": [
      {
        "tenantId": 2,
        "tenantCode": "ACME",
        "tenantName": "ACME Corporation",
        "apiCalls": 45230,
        "rank": 1
      },
      {
        "tenantId": 5,
        "tenantCode": "BIGCORP",
        "tenantName": "Big Corporation",
        "apiCalls": 38950,
        "rank": 2
      },
      {
        "tenantId": 3,
        "tenantCode": "GLOBEX",
        "tenantName": "Globex Corp",
        "apiCalls": 7850,
        "rank": 3
      }
    ]
  }
}
```

#### Casos de Uso

**1. Identificar Clientes VIP**
```javascript
// Top 10 clientes que más usan el sistema
// - Priorizar soporte
// - Ofertas especiales
// - Account management dedicado
```

**2. Revenue Analysis**
```javascript
const top10Revenue = topTenants
  .slice(0, 10)
  .reduce((sum, t) => sum + (t.apiCalls * 0.01), 0);

console.log(`Top 10 clientes generan: $${top10Revenue}`);
// Ej: "Top 10 clientes generan: $9,203"
```

**3. Recursos del Sistema**
```javascript
// Si un cliente está usando demasiado
if (topTenants[0].apiCalls > totalApiCalls * 0.5) {
  alert("⚠️ Un cliente está usando el 50% de los recursos");
}
```

---

### 4. Estadísticas Cross-Tenant

**GET** `/api/v1/admin/metrics/cross-tenant-summary`

**Qué hace:** Estadísticas agregadas de TODOS los tenants  
**Para qué sirve:** Vista global del sistema

#### Request
```bash
curl -X GET http://localhost:8080/api/v1/admin/metrics/cross-tenant-summary \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

#### Response
```json
{
  "success": true,
  "data": {
    "summary": [
      {
        "metricKey": "api.calls.total",
        "tenantCount": 15,
        "totalSum": 128450,
        "average": 8563.33,
        "min": 120,
        "max": 45230,
        "median": 5200
      },
      {
        "metricKey": "api.calls.extract",
        "tenantCount": 15,
        "totalSum": 105820,
        "average": 7054.67,
        "min": 80,
        "max": 38500
      }
    ],
    "systemWide": {
      "totalTenants": 15,
      "activeTenants": 14,
      "totalApiCalls": 128450,
      "totalExtractions": 105820,
      "averageApiCallsPerTenant": 8563
    }
  }
}
```

#### Casos de Uso

**1. Dashboard Ejecutivo**
```javascript
// KPIs principales:
// - Total API calls: 128,450
// - Tenants activos: 14/15
// - Promedio por tenant: 8,563 calls
// - Mayor uso: 45,230 (ACME)
// - Menor uso: 120 (TEST)
```

**2. Capacity Planning**
```javascript
const growthRate = 1.2; // 20% mensual
const nextMonthCalls = systemWide.totalApiCalls * growthRate;
const serverCapacity = 500000;

if (nextMonthCalls > serverCapacity * 0.8) {
  alert("⚠️ Necesitarás más servidores el próximo mes");
}
```

**3. Benchmarking**
```javascript
// Comparar cliente con promedio del sistema
const clientCalls = 45230;
const systemAverage = 8563;

console.log(`Este cliente usa ${clientCalls / systemAverage}x el promedio`);
// "Este cliente usa 5.3x el promedio"
```

---

### 5. Métrica Agregada

**GET** `/api/v1/admin/metrics/aggregated/{key}`

**Qué hace:** Suma una métrica específica de todos los tenants  
**Para qué sirve:** Total del sistema de una métrica

#### Request
```bash
curl -X GET http://localhost:8080/api/v1/admin/metrics/aggregated/api.calls.extract \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

#### Response
```json
{
  "success": true,
  "data": {
    "metricKey": "api.calls.extract",
    "totalValue": 105820,
    "tenantCount": 15,
    "average": 7054.67
  }
}
```

#### Casos de Uso

**1. Reportes Específicos**
```javascript
// ¿Cuántas extracciones exitosas en total?
GET /admin/metrics/aggregated/api.calls.extract.success
// Total: 102,850 extracciones exitosas

// ¿Cuántas fallaron en total?
GET /admin/metrics/aggregated/api.calls.extract.failure
// Total: 2,970 extracciones fallidas

// Tasa de éxito global: 102,850 / 105,820 = 97.2%
```

**2. Monitoreo de Errores**
```javascript
const totalErrors = await getAggregated("errors.total");
const totalCalls = await getAggregated("api.calls.total");

const errorRate = totalErrors / totalCalls;

if (errorRate > 0.05) { // >5%
  sendSlackAlert("⚠️ Tasa de error global es del 5.2%");
}
```

---

### 6. Resumen de Sistema

**GET** `/api/v1/admin/metrics/summary`

**Qué hace:** Snapshot rápido del estado del sistema  
**Para qué sirve:** Vista rápida para monitoreo

#### Request
```bash
curl -X GET http://localhost:8080/api/v1/admin/metrics/summary \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

#### Response
```json
{
  "success": true,
  "data": {
    "totalTenants": 15,
    "activeTenants": 14,
    "totalApiCalls": 128450,
    "totalExtractions": 105820,
    "successRate": 97.19,
    "errorRate": 2.81,
    "topTenant": {
      "code": "ACME",
      "apiCalls": 45230
    },
    "timestamp": "2026-01-14T15:30:00Z"
  }
}
```

#### Casos de Uso

**1. Health Check Dashboard**
```javascript
// Panel de monitoreo en tiempo real
setInterval(async () => {
  const summary = await getSummary();
  
  updateDashboard({
    status: summary.errorRate < 5 ? "Healthy" : "Warning",
    totalCalls: summary.totalApiCalls,
    successRate: summary.successRate + "%"
  });
}, 60000); // Cada minuto
```

**2. Alertas Automáticas**
```javascript
if (summary.errorRate > 10) {
  sendPagerDuty("CRITICAL: Error rate is 10.5%");
}

if (summary.activeTenants < summary.totalTenants * 0.9) {
  sendAlert("Warning: Only 85% of tenants are active");
}
```

---

### 7. Reset de Métricas

**DELETE** `/api/v1/admin/metrics/tenants/{id}`

**Qué hace:** Reinicia métricas de un tenant a cero  
**Para qué sirve:** Inicio de nuevo período de facturación

#### Request
```bash
curl -X DELETE http://localhost:8080/api/v1/admin/metrics/tenants/2 \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

#### Response
```json
{
  "success": true,
  "data": "Metrics reset successfully for tenant 2"
}
```

#### Casos de Uso

**1. Inicio de Mes (Facturación)**
```javascript
// 1ro de cada mes: resetear métricas de todos
const tenants = await getAllTenants();

tenants.forEach(async tenant => {
  // Guardar métricas del mes para facturación
  await saveMonthlyReport(tenant.id, tenant.metrics);
  
  // Resetear para nuevo mes
  await resetMetrics(tenant.id);
});
```

**2. Cambio de Plan**
```javascript
// Cliente upgradea de BASIC a PREMIUM
await updateTenantTier(tenantId, "PREMIUM");

// Resetear métricas para empezar limpio
await resetMetrics(tenantId);
```

**3. Testing/Demo**
```javascript
// Después de una demo
await resetMetrics(demoTenantId);
console.log("Demo metrics cleared");
```

---

## 🎯 Flujo Completo de Uso

### Escenario 1: Cliente Nuevo

```
1. Cliente registrado → Tenant creado
2. Cliente hace login → Métrica: ninguna aún
3. Cliente extrae PDF → api.calls.extract++, api.calls.total++
4. Extracción exitosa → api.calls.extract.success++
5. Cliente ve dashboard → GET /metrics
   Response: { "api.calls.extract": 1, "api.calls.total": 1 }
```

### Escenario 2: Monitoreo de Admin

```
1. Admin hace login como superadmin
2. Admin ve dashboard → GET /admin/metrics/tenants
   Ve todos los clientes y su uso
3. Admin identifica cliente con mucho uso
4. Admin ve detalle → GET /admin/metrics/tenants/{id}
   Analiza métricas específicas
5. Admin decide upgrade del cliente
```

### Escenario 3: Fin de Mes

```
1. Script automático corre 1ro de mes
2. Para cada tenant:
   a. GET /admin/metrics/tenants/{id}
   b. Guardar métricas en tabla monthly_reports
   c. Calcular factura = api.calls.total * rate
   d. DELETE /admin/metrics/tenants/{id} (reset)
3. Enviar facturas a clientes
4. Nuevo mes comienza con métricas en 0
```

---

## 📊 Tabla Resumen de Endpoints

| Endpoint | Quién | Qué Ve | Para Qué |
|----------|-------|---------|----------|
| `GET /metrics` | User/Admin | Su tenant | Ver propio uso |
| `GET /admin/metrics/tenants` | Super Admin | Todos | Dashboard global |
| `GET /admin/metrics/tenants/{id}` | Super Admin | Uno específico | Analizar cliente |
| `GET /admin/metrics/top-tenants` | Super Admin | Top N | Identificar VIPs |
| `GET /admin/metrics/cross-tenant-summary` | Super Admin | Estadísticas globales | KPIs ejecutivos |
| `GET /admin/metrics/aggregated/{key}` | Super Admin | Suma de métrica | Total sistema |
| `GET /admin/metrics/summary` | Super Admin | Snapshot rápido | Health check |
| `DELETE /admin/metrics/tenants/{id}` | Super Admin | - | Reset mensual |

---

## 💡 Casos de Uso Reales

### 1. SaaS Billing (Facturación)

```javascript
// Fin de mes - generar facturas
async function generateMonthlyInvoices() {
  const tenants = await getAllTenants();
  
  for (const tenant of tenants) {
    const metrics = await getTenantMetrics(tenant.id);
    
    // Calcular costo según tier
    let cost = 0;
    switch (tenant.tier) {
      case "FREE":
        cost = 0;
        break;
      case "BASIC":
        cost = 49; // Base
        break;
      case "PREMIUM":
        cost = 199; // Base
        break;
      case "PAY_PER_USE":
        cost = metrics["api.calls.total"] * 0.01; // $0.01 por call
        break;
    }
    
    // Crear invoice
    await createInvoice(tenant.id, cost, metrics);
    
    // Reset métricas
    await resetMetrics(tenant.id);
  }
}
```

### 2. Alertas de Quota

```javascript
// Correr cada hora
async function checkQuotas() {
  const tenants = await getAllTenants();
  
  for (const tenant of tenants) {
    const used = tenant.metrics["api.calls.total"];
    const quota = tenant.maxApiCallsPerMonth;
    const percent = (used / quota) * 100;
    
    if (percent >= 90) {
      await sendEmail(tenant.contactEmail, {
        subject: "⚠️ Quota Warning: 90% Used",
        body: `You've used ${percent}% of your monthly quota.`
      });
    }
    
    if (percent >= 100) {
      await disableTenant(tenant.id);
      await sendEmail(tenant.contactEmail, {
        subject: "🚫 Quota Exceeded - Service Suspended",
        body: "Your monthly quota has been exceeded."
      });
    }
  }
}
```

### 3. Dashboard de Usuario

```javascript
// Frontend del cliente
async function loadUserDashboard() {
  const metrics = await fetch("/api/v1/metrics", {
    headers: { Authorization: `Bearer ${token}` }
  }).then(r => r.json());
  
  const data = metrics.data;
  const quota = user.tenant.maxApiCallsPerMonth;
  
  return {
    totalCalls: data["api.calls.total"],
    extractions: data["api.calls.extract"],
    successRate: (data["api.calls.extract.success"] / data["api.calls.extract"] * 100).toFixed(2),
    quotaUsed: ((data["api.calls.total"] / quota) * 100).toFixed(2),
    remainingCalls: quota - data["api.calls.total"]
  };
}

// Mostrar:
// ┌──────────────────────────────────┐
// │ API Usage This Month             │
// ├──────────────────────────────────┤
// │ Total Calls: 450                 │
// │ PDF Extractions: 380             │
// │ Success Rate: 94.7%              │
// │                                  │
// │ Quota: 450 / 1,000,000 (0.045%) │
// │ [████░░░░░░░░░░░░░░░░] 0.045%   │
// │                                  │
// │ Remaining: 999,550 calls         │
// └──────────────────────────────────┘
```

### 4. Support Dashboard

```javascript
// Panel de soporte técnico
async function loadSupportDashboard(tenantId) {
  const details = await fetch(`/api/v1/admin/metrics/tenants/${tenantId}`, {
    headers: { Authorization: `Bearer ${adminToken}` }
  }).then(r => r.json());
  
  const metrics = details.data.metrics;
  const errorRate = (metrics["errors.total"] / metrics["api.calls.total"]) * 100;
  
  return {
    tenant: details.data.tenant,
    health: errorRate < 5 ? "Healthy" : "Issues",
    totalCalls: metrics["api.calls.total"],
    errorRate: errorRate.toFixed(2) + "%",
    commonErrors: {
      validation: metrics["errors.validation"],
      processing: metrics["errors.processing"]
    },
    recommendation: errorRate > 10 
      ? "⚠️ High error rate - investigate logs"
      : "✅ Normal operation"
  };
}
```

---

## ✅ Resumen de Funcionalidad

**Las métricas sirven para:**

1. **Facturación** - Cobrar según uso real
2. **Quotas** - Controlar límites de cada cliente
3. **Monitoreo** - Detectar problemas temprano
4. **Analytics** - Entender cómo usan el sistema
5. **Soporte** - Ayudar a clientes con problemas
6. **Planning** - Predecir necesidades de infraestructura
7. **Sales** - Identificar clientes para upsell

**Se registran automáticamente** - El usuario no hace nada especial.

**Dos niveles:**
- **Usuario:** Ve solo su tenant
- **Super Admin:** Ve todo el sistema

---

¿Quieres que profundice en algún caso de uso específico o endpoint en particular?
