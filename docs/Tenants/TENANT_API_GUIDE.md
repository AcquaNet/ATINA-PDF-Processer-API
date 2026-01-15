# 🏢 API de Gestión de Tenants - Guía Completa

## 📋 Endpoints Disponibles

Todos los endpoints requieren rol **SYSTEM_ADMIN**.

### Base URL
```
/api/v1/admin/tenants
```

---

## 📝 Endpoints CRUD

### 1. Crear Tenant

**POST** `/api/v1/admin/tenants`

Crea un nuevo tenant en el sistema.

**Request Body:**
```json
{
  "tenantCode": "MYCOMPANY",
  "tenantName": "My Company Inc.",
  "contactEmail": "admin@mycompany.com",
  "subscriptionTier": "PREMIUM",
  "maxApiCallsPerMonth": 500000,
  "maxStorageMb": 5000,
  "enabled": true
}
```

**Validaciones:**
- `tenantCode`: Obligatorio, 2-50 caracteres, solo mayúsculas, números, guiones y guiones bajos
- `tenantName`: Obligatorio, 2-200 caracteres
- `contactEmail`: Obligatorio, formato email válido
- `subscriptionTier`: Obligatorio, uno de: FREE, BASIC, PREMIUM, UNLIMITED
- `maxApiCallsPerMonth`: Opcional, número no negativo
- `maxStorageMb`: Opcional, número no negativo
- `enabled`: Opcional, por defecto true

**Response:**
```json
{
  "success": true,
  "data": {
    "id": 5,
    "tenantCode": "MYCOMPANY",
    "tenantName": "My Company Inc.",
    "contactEmail": "admin@mycompany.com",
    "subscriptionTier": "PREMIUM",
    "maxApiCallsPerMonth": 500000,
    "maxStorageMb": 5000,
    "enabled": true,
    "createdAt": "2026-01-14T14:00:00Z",
    "updatedAt": "2026-01-14T14:00:00Z",
    "totalUsers": 0,
    "totalApiCalls": 0
  }
}
```

**cURL:**
```bash
curl -X POST http://localhost:8080/api/v1/admin/tenants \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "tenantCode": "MYCOMPANY",
    "tenantName": "My Company Inc.",
    "contactEmail": "admin@mycompany.com",
    "subscriptionTier": "PREMIUM",
    "maxApiCallsPerMonth": 500000,
    "maxStorageMb": 5000
  }' | jq '.'
```

---

### 2. Listar Todos los Tenants

**GET** `/api/v1/admin/tenants`

Obtiene lista de todos los tenants con sus estadísticas.

**Response:**
```json
{
  "success": true,
  "data": [
    {
      "id": 1,
      "tenantCode": "SYSTEM",
      "tenantName": "System Administration",
      "subscriptionTier": "UNLIMITED",
      "enabled": true,
      "totalUsers": 1,
      "totalApiCalls": 0
    },
    {
      "id": 2,
      "tenantCode": "ACME",
      "tenantName": "ACME Corporation",
      "subscriptionTier": "PREMIUM",
      "maxApiCallsPerMonth": 1000000,
      "enabled": true,
      "totalUsers": 2,
      "totalApiCalls": 150
    }
  ]
}
```

**cURL:**
```bash
curl -X GET http://localhost:8080/api/v1/admin/tenants \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq '.'
```

---

### 3. Ver Tenant por ID

**GET** `/api/v1/admin/tenants/{id}`

Obtiene información detallada de un tenant específico.

**Response:**
```json
{
  "success": true,
  "data": {
    "id": 2,
    "tenantCode": "ACME",
    "tenantName": "ACME Corporation",
    "contactEmail": "admin@acme.com",
    "subscriptionTier": "PREMIUM",
    "maxApiCallsPerMonth": 1000000,
    "maxStorageMb": 10000,
    "enabled": true,
    "createdAt": "2026-01-14T10:00:00Z",
    "updatedAt": "2026-01-14T10:00:00Z",
    "totalUsers": 2,
    "totalApiCalls": 150
  }
}
```

**cURL:**
```bash
curl -X GET http://localhost:8080/api/v1/admin/tenants/2 \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq '.'
```

---

### 4. Ver Tenant por Code

**GET** `/api/v1/admin/tenants/code/{code}`

Busca tenant por su código.

**cURL:**
```bash
curl -X GET http://localhost:8080/api/v1/admin/tenants/code/ACME \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq '.'
```

---

### 5. Actualizar Tenant

**PUT** `/api/v1/admin/tenants/{id}`

Actualiza información de un tenant. Solo se actualizan los campos proporcionados.

**Request Body (todos opcionales):**
```json
{
  "tenantName": "ACME Corp Updated",
  "contactEmail": "newadmin@acme.com",
  "subscriptionTier": "UNLIMITED",
  "maxApiCallsPerMonth": 2000000,
  "maxStorageMb": 20000,
  "enabled": true
}
```

**cURL:**
```bash
curl -X PUT http://localhost:8080/api/v1/admin/tenants/2 \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "subscriptionTier": "UNLIMITED",
    "maxApiCallsPerMonth": 2000000
  }' | jq '.'
```

---

### 6. Eliminar Tenant

**DELETE** `/api/v1/admin/tenants/{id}`

Elimina un tenant. Solo es posible si el tenant **no tiene usuarios**.

**Response:**
```json
{
  "success": true,
  "data": "Tenant deleted successfully"
}
```

**cURL:**
```bash
curl -X DELETE http://localhost:8080/api/v1/admin/tenants/5 \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq '.'
```

**Error si tiene usuarios:**
```json
{
  "success": false,
  "error": "Cannot delete tenant with existing users. Disable it instead."
}
```

---

### 7. Habilitar Tenant

**PATCH** `/api/v1/admin/tenants/{id}/enable`

Habilita un tenant deshabilitado.

**cURL:**
```bash
curl -X PATCH http://localhost:8080/api/v1/admin/tenants/2/enable \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq '.'
```

---

### 8. Deshabilitar Tenant

**PATCH** `/api/v1/admin/tenants/{id}/disable`

Deshabilita un tenant. Los usuarios de este tenant no podrán acceder al sistema.

**cURL:**
```bash
curl -X PATCH http://localhost:8080/api/v1/admin/tenants/2/disable \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq '.'
```

---

### 9. Listar Tenants por Tier

**GET** `/api/v1/admin/tenants/tier/{tier}`

Filtra tenants por nivel de suscripción.

**Tiers válidos:** FREE, BASIC, PREMIUM, UNLIMITED

**cURL:**
```bash
curl -X GET http://localhost:8080/api/v1/admin/tenants/tier/PREMIUM \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq '.'
```

---

## 🚀 Ejemplos de Uso Completos

### Ejemplo 1: Crear Nuevo Cliente

```bash
# 1. Login como superadmin
ADMIN_TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"superadmin","password":"superadmin123"}' \
  | jq -r '.data.token')

# 2. Crear tenant
curl -X POST http://localhost:8080/api/v1/admin/tenants \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "tenantCode": "NEWCORP",
    "tenantName": "New Corporation",
    "contactEmail": "admin@newcorp.com",
    "subscriptionTier": "BASIC",
    "maxApiCallsPerMonth": 100000,
    "maxStorageMb": 2000
  }' | jq '.'

# 3. Verificar tenant creado
curl -X GET http://localhost:8080/api/v1/admin/tenants/code/NEWCORP \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq '.'
```

---

### Ejemplo 2: Upgrade de Tenant

```bash
# Actualizar de BASIC a PREMIUM
curl -X PUT http://localhost:8080/api/v1/admin/tenants/2 \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "subscriptionTier": "PREMIUM",
    "maxApiCallsPerMonth": 1000000,
    "maxStorageMb": 10000
  }' | jq '.'
```

---

### Ejemplo 3: Suspender Cliente

```bash
# Deshabilitar tenant temporalmente
curl -X PATCH http://localhost:8080/api/v1/admin/tenants/3/disable \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq '.'

# Reactivar después
curl -X PATCH http://localhost:8080/api/v1/admin/tenants/3/enable \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq '.'
```

---

### Ejemplo 4: Ver Dashboard de Tenants

```bash
# 1. Listar todos los tenants
curl -s -X GET http://localhost:8080/api/v1/admin/tenants \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  | jq '.data[] | {
      code: .tenantCode,
      name: .tenantName,
      tier: .subscriptionTier,
      users: .totalUsers,
      apiCalls: .totalApiCalls,
      enabled: .enabled
    }'

# Output:
# {
#   "code": "ACME",
#   "name": "ACME Corporation",
#   "tier": "PREMIUM",
#   "users": 2,
#   "apiCalls": 150,
#   "enabled": true
# }
```

---

## 📊 Tiers de Suscripción

| Tier | Descripción | API Calls Sugeridos | Storage Sugerido |
|------|-------------|---------------------|------------------|
| **FREE** | Prueba gratuita | 10,000/mes | 100 MB |
| **BASIC** | Pequeñas empresas | 100,000/mes | 2 GB |
| **PREMIUM** | Empresas medianas | 1,000,000/mes | 10 GB |
| **UNLIMITED** | Enterprise/Sistema | Sin límite (null) | Sin límite (null) |

---

## 🔒 Permisos

**Todos los endpoints requieren:**
- Header: `Authorization: Bearer {token}`
- Role: `SYSTEM_ADMIN`

**Si intentas acceder sin permisos:**
```json
{
  "success": false,
  "error": "Access Denied"
}
```

---

## ⚠️ Reglas Importantes

### 1. Códigos de Tenant
- Deben ser únicos
- Solo mayúsculas, números, guiones y guiones bajos
- Ejemplos válidos: `ACME`, `CORP_123`, `CLIENT-A`
- Ejemplos inválidos: `acme` (minúsculas), `Corp 1` (espacios)

### 2. Eliminación de Tenants
- Solo se puede eliminar si **no tiene usuarios**
- Si tiene usuarios, primero debes:
  - Eliminar todos los usuarios, O
  - Deshabilitar el tenant (recomendado)

### 3. Deshabilitación
- Cuando deshabilitas un tenant, **todos sus usuarios** pierden acceso
- El tenant y sus datos se mantienen
- Puedes reactivarlo en cualquier momento

### 4. Límites
- `maxApiCallsPerMonth`: null = sin límite
- `maxStorageMb`: null = sin límite
- Tier UNLIMITED debería usar null para ambos

---

## 📝 Response Completo

Todos los tenants incluyen:

```json
{
  "id": 2,                          // ID único
  "tenantCode": "ACME",             // Código único
  "tenantName": "ACME Corp",        // Nombre display
  "contactEmail": "admin@acme.com", // Email contacto
  "subscriptionTier": "PREMIUM",    // Tier de suscripción
  "maxApiCallsPerMonth": 1000000,   // Límite API calls
  "maxStorageMb": 10000,            // Límite storage
  "enabled": true,                  // Estado activo/inactivo
  "createdAt": "2026-01-14T10:00:00Z",
  "updatedAt": "2026-01-14T14:30:00Z",
  "totalUsers": 5,                  // Usuarios del tenant
  "totalApiCalls": 45230            // API calls consumidos
}
```

---

## 🧪 Testing Postman

### Collection Variables:
```
baseUrl: http://localhost:8080
adminToken: {{token del superadmin}}
```

### Requests:
1. Login Superadmin → Guardar token
2. Create Tenant → POST /admin/tenants
3. List All → GET /admin/tenants
4. Get by ID → GET /admin/tenants/{id}
5. Update → PUT /admin/tenants/{id}
6. Disable → PATCH /admin/tenants/{id}/disable
7. Enable → PATCH /admin/tenants/{id}/enable
8. Delete → DELETE /admin/tenants/{id}

---

## 🎯 Workflow Típico

### Nuevo Cliente:
1. **POST** `/admin/tenants` - Crear tenant
2. **POST** `/admin/users` - Crear usuario admin del tenant
3. **GET** `/admin/tenants/{id}` - Verificar creación

### Suspensión Temporal:
1. **PATCH** `/admin/tenants/{id}/disable` - Suspender
2. Usuario intenta login → Error: "Tenant disabled"
3. **PATCH** `/admin/tenants/{id}/enable` - Reactivar

### Upgrade/Downgrade:
1. **PUT** `/admin/tenants/{id}` - Cambiar tier y límites
2. Cambios aplicados inmediatamente

### Eliminación:
1. **GET** `/admin/tenants/{id}` - Verificar totalUsers
2. Si totalUsers > 0 → Eliminar usuarios primero
3. **DELETE** `/admin/tenants/{id}` - Eliminar

---

**¡Ahora puedes gestionar tenants completamente desde la API!** 🎉
