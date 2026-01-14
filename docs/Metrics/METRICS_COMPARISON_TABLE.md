# 📊 Tabla Comparativa de APIs de Métricas

## 🎯 Resumen Ejecutivo

### ¿Qué son las métricas?
Contadores automáticos que registran actividad del sistema (extracciones, errores, uso).

### ¿Para qué sirven?
- 💰 **Facturación** - Cobrar según uso real
- 📊 **Quotas** - Controlar límites
- 🔍 **Monitoreo** - Detectar problemas
- 📈 **Analytics** - Entender comportamiento
- 🎯 **Business Intelligence** - Tomar decisiones

---

## 📋 Tabla Completa de Endpoints

| # | Endpoint | Método | Rol Requerido | Scope | Qué Hace | Para Qué Sirve |
|---|----------|--------|---------------|-------|----------|----------------|
| 1 | `/api/v1/metrics` | GET | USER o ADMIN | Propio tenant | Muestra métricas de mi tenant | Dashboard personal/equipo |
| 2 | `/api/v1/admin/metrics/tenants` | GET | SYSTEM_ADMIN | Todos los tenants | Lista todos los tenants con métricas | Dashboard global del sistema |
| 3 | `/api/v1/admin/metrics/tenants/{id}` | GET | SYSTEM_ADMIN | Un tenant específico | Detalle completo de un tenant | Análisis de cliente específico |
| 4 | `/api/v1/admin/metrics/tenants/code/{code}` | GET | SYSTEM_ADMIN | Un tenant por código | Buscar tenant por código | Búsqueda rápida |
| 5 | `/api/v1/admin/metrics/top-tenants` | GET | SYSTEM_ADMIN | Top N tenants | Los N clientes con más uso | Identificar VIPs |
| 6 | `/api/v1/admin/metrics/cross-tenant-summary` | GET | SYSTEM_ADMIN | Todos (agregado) | Estadísticas globales del sistema | KPIs ejecutivos |
| 7 | `/api/v1/admin/metrics/aggregated/{key}` | GET | SYSTEM_ADMIN | Todos (suma) | Suma una métrica de todos | Total sistema de métrica |
| 8 | `/api/v1/admin/metrics/summary` | GET | SYSTEM_ADMIN | Sistema completo | Snapshot rápido del estado | Health check / Monitoreo |
| 9 | `/api/v1/admin/metrics/system` | GET | SYSTEM_ADMIN | Métricas del sistema | Info de sistema (no tenants) | Métricas de infraestructura |
| 10 | `/api/v1/admin/metrics/tenants/{id}` | DELETE | SYSTEM_ADMIN | Un tenant | Resetea métricas a 0 | Inicio de mes / Billing |

---

## 🔐 Matriz de Permisos

| Persona | Role | Puede Ver | No Puede Ver |
|---------|------|-----------|--------------|
| **Usuario Regular** (acme-user) | USER | - Su tenant (ACME)<br>- Sus propias métricas | - Otros tenants<br>- Métricas globales<br>- Admin endpoints |
| **Admin del Tenant** (acme-admin) | ADMIN | - Su tenant (ACME)<br>- Métricas de todo su tenant | - Otros tenants<br>- Métricas globales<br>- Admin endpoints |
| **Super Admin** (superadmin) | SYSTEM_ADMIN | - **TODO**<br>- Todos los tenants<br>- Métricas globales<br>- Estadísticas del sistema | Nada - acceso completo |

---

## 📊 Métricas Registradas Automáticamente

### Categoría: API Calls

| Métrica Key | Cuándo Se Incrementa | Ejemplo de Uso |
|-------------|---------------------|----------------|
| `api.calls.total` | En **CADA** request | Uso total del sistema |
| `api.calls.extract` | Al extraer PDF | Total de extracciones |
| `api.calls.extract.success` | Extracción exitosa | Extracciones correctas |
| `api.calls.extract.failure` | Extracción fallida | Extracciones con error |
| `api.calls.validate` | Al validar datos | Total de validaciones |
| `api.calls.batch` | Procesar batch | Operaciones por lote |
| `api.calls.batch.items` | Items en batch | Cantidad procesada |
| `api.calls.ai.generate` | Usar AI | Uso de IA generativa |

### Categoría: Errores

| Métrica Key | Cuándo Se Incrementa | Ejemplo de Uso |
|-------------|---------------------|----------------|
| `errors.total` | En cualquier error | Total de errores |
| `errors.validation` | Error de validación | Errores de datos de entrada |
| `errors.processing` | Error de procesamiento | Errores internos del sistema |

---

## 🎯 Casos de Uso por Endpoint

### Endpoint 1: GET /api/v1/metrics
**Usuario:** USER o ADMIN de tenant  
**Ve:** Solo su tenant  

**Casos de Uso:**
```
✓ Ver mi uso mensual
✓ Dashboard personal
✓ Verificar quota restante
✓ Analizar tasa de éxito
✓ Monitorear errores propios
```

**Ejemplo Real:**
```javascript
// Dashboard del usuario
const metrics = await getMetrics();
display({
  pdfsProcessed: metrics["api.calls.extract"],
  successRate: (metrics["api.calls.extract.success"] / metrics["api.calls.extract"] * 100),
  quotaUsed: (metrics["api.calls.total"] / user.quota * 100)
});
```

---

### Endpoint 2: GET /admin/metrics/tenants
**Usuario:** SYSTEM_ADMIN  
**Ve:** Todos los tenants  

**Casos de Uso:**
```
✓ Dashboard global del sistema
✓ Ver todos los clientes
✓ Comparar uso entre tenants
✓ Detectar clientes con problemas
✓ Identificar candidatos para upgrade
```

**Ejemplo Real:**
```javascript
// Panel de administración
const allTenants = await getAllTenantMetrics();
const warnings = allTenants.filter(t => t.quotaUsagePercent > 90);
const inactive = allTenants.filter(t => t.totalApiCalls === 0);

sendSlackAlert(`⚠️ ${warnings.length} clientes cerca de su límite`);
sendSlackAlert(`😴 ${inactive.length} clientes sin actividad`);
```

---

### Endpoint 3: GET /admin/metrics/tenants/{id}
**Usuario:** SYSTEM_ADMIN  
**Ve:** Un tenant específico en detalle  

**Casos de Uso:**
```
✓ Soporte técnico de cliente
✓ Análisis profundo de uso
✓ Investigar problemas
✓ Preparar upgrade/downgrade
✓ Auditar actividad
```

**Ejemplo Real:**
```javascript
// Ticket de soporte: "Cliente reporta muchos errores"
const details = await getTenantDetails(clientId);
const errorRate = details.metrics["errors.total"] / details.metrics["api.calls.total"];

if (errorRate > 0.10) {
  console.log("ALTA tasa de error (>10%) - revisar logs");
  console.log("Errores de validación:", details.metrics["errors.validation"]);
  console.log("Errores de procesamiento:", details.metrics["errors.processing"]);
}
```

---

### Endpoint 4: GET /admin/metrics/top-tenants
**Usuario:** SYSTEM_ADMIN  
**Ve:** Top N clientes por uso  

**Casos de Uso:**
```
✓ Identificar clientes VIP
✓ Priorizar soporte
✓ Account management
✓ Análisis de revenue
✓ Capacity planning
```

**Ejemplo Real:**
```javascript
// Identificar top 10 clientes para account manager
const top10 = await getTopTenants(10);

top10.forEach(tenant => {
  assignAccountManager(tenant.id, "senior-am");
  offerPremiumSupport(tenant.id);
  
  console.log(`${tenant.name}: ${tenant.apiCalls} calls - VIP status`);
});
```

---

### Endpoint 5: GET /admin/metrics/cross-tenant-summary
**Usuario:** SYSTEM_ADMIN  
**Ve:** Estadísticas agregadas de todo el sistema  

**Casos de Uso:**
```
✓ KPIs ejecutivos
✓ Reportes mensuales
✓ Capacity planning
✓ Benchmarking
✓ Presentaciones a stakeholders
```

**Ejemplo Real:**
```javascript
// Reporte mensual para ejecutivos
const summary = await getCrossTenantSummary();

const report = {
  totalClients: summary.systemWide.totalTenants,
  activeClients: summary.systemWide.activeTenants,
  totalApiCalls: summary.systemWide.totalApiCalls,
  averagePerClient: summary.systemWide.averageApiCallsPerTenant,
  growthVsLastMonth: calculateGrowth(summary, lastMonthData)
};

sendEmailReport(executives, report);
```

---

### Endpoint 6: GET /admin/metrics/aggregated/{key}
**Usuario:** SYSTEM_ADMIN  
**Ve:** Suma de una métrica específica  

**Casos de Uso:**
```
✓ Total de una métrica en el sistema
✓ Reportes específicos
✓ Monitoreo de errores global
✓ Cálculos de revenue
✓ Estadísticas puntuales
```

**Ejemplo Real:**
```javascript
// Monitoreo de errores del sistema
const totalErrors = await getAggregatedMetric("errors.total");
const totalCalls = await getAggregatedMetric("api.calls.total");

const globalErrorRate = (totalErrors / totalCalls) * 100;

if (globalErrorRate > 5) {
  sendPagerDuty({
    severity: "HIGH",
    message: `Error rate is ${globalErrorRate}% (threshold: 5%)`
  });
}
```

---

### Endpoint 7: GET /admin/metrics/summary
**Usuario:** SYSTEM_ADMIN  
**Ve:** Snapshot rápido del sistema  

**Casos de Uso:**
```
✓ Health check dashboard
✓ Monitoreo en tiempo real
✓ Status page
✓ Alertas automáticas
✓ Quick overview
```

**Ejemplo Real:**
```javascript
// Health check cada minuto
setInterval(async () => {
  const summary = await getSystemSummary();
  
  updateDashboard({
    status: summary.errorRate < 5 ? "🟢 Healthy" : "🟡 Warning",
    totalCalls: summary.totalApiCalls.toLocaleString(),
    activeTenants: `${summary.activeTenants}/${summary.totalTenants}`,
    successRate: `${summary.successRate}%`,
    topClient: summary.topTenant.code
  });
}, 60000);
```

---

### Endpoint 8: DELETE /admin/metrics/tenants/{id}
**Usuario:** SYSTEM_ADMIN  
**Hace:** Resetea métricas de un tenant a 0  

**Casos de Uso:**
```
✓ Inicio de mes (billing cycle)
✓ Cambio de plan
✓ Reset después de demo
✓ Limpiar datos de testing
✓ Nueva facturación
```

**Ejemplo Real:**
```javascript
// Script que corre el 1ro de cada mes
async function monthlyBillingCycle() {
  const tenants = await getAllTenants();
  
  for (const tenant of tenants) {
    // 1. Leer métricas del mes
    const metrics = await getTenantMetrics(tenant.id);
    
    // 2. Guardar para facturación
    await saveMonthlyReport({
      tenantId: tenant.id,
      month: getCurrentMonth(),
      metrics: metrics,
      cost: calculateCost(tenant.tier, metrics)
    });
    
    // 3. Resetear métricas
    await resetMetrics(tenant.id);
    
    // 4. Enviar factura
    await sendInvoice(tenant.id);
  }
}

// Correr el 1ro de cada mes a las 00:00
schedule("0 0 1 * *", monthlyBillingCycle);
```

---

## 💡 Ejemplos de Integración

### Dashboard de Usuario (Frontend)

```javascript
// components/UserDashboard.jsx
import { useState, useEffect } from 'react';

function UserDashboard() {
  const [metrics, setMetrics] = useState(null);
  
  useEffect(() => {
    fetch('/api/v1/metrics', {
      headers: { Authorization: `Bearer ${token}` }
    })
    .then(r => r.json())
    .then(data => setMetrics(data.data));
  }, []);
  
  if (!metrics) return <Loading />;
  
  const quota = user.tenant.maxApiCallsPerMonth;
  const used = metrics["api.calls.total"];
  const quotaPercent = (used / quota * 100).toFixed(2);
  
  return (
    <div className="dashboard">
      <h1>Mi Uso Este Mes</h1>
      
      <Card title="API Calls">
        <p>Usadas: {used.toLocaleString()}</p>
        <p>Quota: {quota.toLocaleString()}</p>
        <ProgressBar value={quotaPercent} />
        <p>{quotaPercent}% usado</p>
      </Card>
      
      <Card title="Extracciones">
        <p>Total: {metrics["api.calls.extract"]}</p>
        <p>Exitosas: {metrics["api.calls.extract.success"]} ✅</p>
        <p>Fallidas: {metrics["api.calls.extract.failure"]} ❌</p>
      </Card>
    </div>
  );
}
```

### Dashboard de Admin (Backend)

```python
# admin_dashboard.py
from flask import Flask, jsonify
import requests

app = Flask(__name__)

@app.route('/admin/dashboard')
def admin_dashboard():
    # Get all tenants
    response = requests.get(
        'http://localhost:8080/api/v1/admin/metrics/tenants',
        headers={'Authorization': f'Bearer {admin_token}'}
    )
    tenants = response.json()['data']
    
    # Calculate KPIs
    total_clients = len(tenants)
    active_clients = sum(1 for t in tenants.values() if t['totalApiCalls'] > 0)
    total_calls = sum(t['totalApiCalls'] for t in tenants.values())
    
    # Find clients needing attention
    warnings = [
        t for t in tenants.values() 
        if t.get('quotaUsagePercent', 0) > 90
    ]
    
    return jsonify({
        'kpis': {
            'totalClients': total_clients,
            'activeClients': active_clients,
            'totalApiCalls': total_calls
        },
        'warnings': warnings,
        'tenants': tenants
    })
```

### Sistema de Alertas (Monitoring)

```python
# monitoring.py
import schedule
import time

def check_system_health():
    """Runs every 5 minutes"""
    summary = get_system_summary()
    
    # Alert on high error rate
    if summary['errorRate'] > 5:
        send_alert(
            severity='WARNING',
            message=f'System error rate: {summary["errorRate"]}%'
        )
    
    # Alert on inactive tenants
    inactive_ratio = 1 - (summary['activeTenants'] / summary['totalTenants'])
    if inactive_ratio > 0.2:  # >20% inactive
        send_alert(
            severity='INFO',
            message=f'{inactive_ratio*100}% of tenants are inactive'
        )
    
    # Alert on quota warnings
    tenants = get_all_tenants()
    quota_warnings = [
        t for t in tenants 
        if t['quotaUsagePercent'] > 80
    ]
    
    if quota_warnings:
        for tenant in quota_warnings:
            send_email(
                to=tenant['contactEmail'],
                subject=f'Quota Warning: {tenant["quotaUsagePercent"]}% used',
                body=f'You have used {tenant["quotaUsagePercent"]}% of your quota.'
            )

# Run every 5 minutes
schedule.every(5).minutes.do(check_system_health)

while True:
    schedule.run_pending()
    time.sleep(60)
```

---

## 🎯 Resumen de Valor de Negocio

| Funcionalidad | Valor de Negocio | ROI |
|---------------|------------------|-----|
| **Billing Automático** | Facturación precisa según uso real | 💰 Alto - Reduce trabajo manual |
| **Quota Management** | Previene abuso, protege infraestructura | 🛡️ Alto - Ahorra costos |
| **Alertas Proactivas** | Detecta problemas antes que el cliente | 😊 Alto - Mejor satisfacción |
| **Analytics** | Decisiones basadas en datos | 📊 Medio - Optimización continua |
| **VIP Identification** | Atención personalizada a top clientes | 💎 Alto - Retención de clientes |
| **Capacity Planning** | Predice necesidades de infraestructura | 📈 Medio - Evita sobre/sub provisión |

---

## ✅ Checklist de Implementación

Para aprovechar las métricas al máximo:

- [ ] **Dashboard de Usuario** - Mostrar uso y quota
- [ ] **Dashboard de Admin** - Vista global de todos los tenants
- [ ] **Sistema de Alertas** - Notificar cuando quota > 80%
- [ ] **Billing Automático** - Script mensual de facturación
- [ ] **Monitoreo** - Health check cada 5 minutos
- [ ] **Reportes** - Reporte mensual para ejecutivos
- [ ] **Support Tools** - Panel para analizar clientes
- [ ] **Capacity Planning** - Predecir crecimiento

---

**¿Quieres implementación de algún caso de uso específico?** 🚀
