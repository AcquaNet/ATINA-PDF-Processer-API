# 📚 API de Usuarios - Documentación

## 🔐 Autenticación

Todos los endpoints requieren autenticación JWT.

```bash
# 1. Login para obtener token
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "superadmin",
    "password": "superadmin123"
  }'

# Respuesta:
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "type": "Bearer"
}

# 2. Usar el token en requests subsiguientes
# Agregar header: Authorization: Bearer <token>
```

---

## 📋 Endpoints Disponibles

### 1. Listar Usuarios (Paginado)

```bash
GET /api/v1/users?page=0&size=10&sortBy=username&sortDirection=asc
```

**Parámetros:**
- `page` (opcional): Número de página (default: 0)
- `size` (opcional): Tamaño de página (default: 10)
- `sortBy` (opcional): Campo para ordenar (default: id)
- `sortDirection` (opcional): asc o desc (default: asc)

**Ejemplo:**

```bash
curl -X GET "http://localhost:8080/api/v1/users?page=0&size=10" \
  -H "Authorization: Bearer <token>"
```

**Respuesta:**

```json
{
  "users": [
    {
      "id": 1,
      "username": "superadmin",
      "email": "superadmin@system.internal",
      "fullName": "System Super Administrator",
      "role": "SYSTEM_ADMIN",
      "enabled": true,
      "createdAt": "2026-01-18T18:00:00Z",
      "lastLoginAt": null,
      "tenant": {
        "id": 1,
        "tenantCode": "SYSTEM",
        "tenantName": "System Administration"
      }
    }
  ],
  "currentPage": 0,
  "totalItems": 5,
  "totalPages": 1
}
```

---

### 2. Obtener Usuario por ID

```bash
GET /api/v1/users/{id}
```

**Ejemplo:**

```bash
curl -X GET http://localhost:8080/api/v1/users/1 \
  -H "Authorization: Bearer <token>"
```

**Respuesta:**

```json
{
  "id": 1,
  "username": "superadmin",
  "email": "superadmin@system.internal",
  "fullName": "System Super Administrator",
  "role": "SYSTEM_ADMIN",
  "enabled": true,
  "createdAt": "2026-01-18T18:00:00Z",
  "lastLoginAt": null,
  "tenant": {
    "id": 1,
    "tenantCode": "SYSTEM",
    "tenantName": "System Administration"
  }
}
```

---

### 3. Obtener Usuario por Username

```bash
GET /api/v1/users/username/{username}
```

**Ejemplo:**

```bash
curl -X GET http://localhost:8080/api/v1/users/username/acme-admin \
  -H "Authorization: Bearer <token>"
```

---

### 4. Crear Usuario

```bash
POST /api/v1/users
```

**Body:**

```json
{
  "username": "john-doe",
  "password": "securepass123",
  "email": "john.doe@acme.com",
  "fullName": "John Doe",
  "tenantCode": "ACME",
  "role": "USER",
  "enabled": true
}
```

**Ejemplo:**

```bash
curl -X POST http://localhost:8080/api/v1/users \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "john-doe",
    "password": "securepass123",
    "email": "john.doe@acme.com",
    "fullName": "John Doe",
    "tenantCode": "ACME",
    "role": "USER",
    "enabled": true
  }'
```

**Respuesta:**

```json
{
  "id": 10,
  "username": "john-doe",
  "email": "john.doe@acme.com",
  "fullName": "John Doe",
  "role": "USER",
  "enabled": true,
  "createdAt": "2026-01-18T20:00:00Z",
  "lastLoginAt": null,
  "tenant": {
    "id": 2,
    "tenantCode": "ACME",
    "tenantName": "ACME Corporation"
  }
}
```

---

### 5. Actualizar Usuario

```bash
PUT /api/v1/users/{id}
```

**Body (todos los campos son opcionales):**

```json
{
  "email": "newemail@acme.com",
  "fullName": "John Doe Updated",
  "role": "ADMIN",
  "enabled": true,
  "password": "newpassword123"
}
```

**Ejemplo:**

```bash
curl -X PUT http://localhost:8080/api/v1/users/10 \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{
    "email": "john.updated@acme.com",
    "fullName": "John Doe Updated"
  }'
```

---

### 6. Deshabilitar Usuario

```bash
PATCH /api/v1/users/{id}/disable
```

**Ejemplo:**

```bash
curl -X PATCH http://localhost:8080/api/v1/users/10/disable \
  -H "Authorization: Bearer <token>"
```

---

### 7. Habilitar Usuario

```bash
PATCH /api/v1/users/{id}/enable
```

**Ejemplo:**

```bash
curl -X PATCH http://localhost:8080/api/v1/users/10/enable \
  -H "Authorization: Bearer <token>"
```

---

### 8. Eliminar Usuario (Hard Delete)

```bash
DELETE /api/v1/users/{id}
```

**⚠️ Requiere rol SYSTEM_ADMIN**

**Ejemplo:**

```bash
curl -X DELETE http://localhost:8080/api/v1/users/10 \
  -H "Authorization: Bearer <token>"
```

---

### 9. Obtener Usuarios por Tenant

```bash
GET /api/v1/users/tenant/{tenantCode}
```

**Ejemplo:**

```bash
curl -X GET http://localhost:8080/api/v1/users/tenant/ACME \
  -H "Authorization: Bearer <token>"
```

**Respuesta:**

```json
[
  {
    "id": 2,
    "username": "acme-admin",
    "email": "admin@acme.com",
    "fullName": "ACME Administrator",
    "role": "ADMIN",
    "enabled": true,
    "createdAt": "2026-01-18T18:00:00Z",
    "tenant": {
      "id": 2,
      "tenantCode": "ACME",
      "tenantName": "ACME Corporation"
    }
  }
]
```

---

## 🔒 Permisos por Endpoint

| Endpoint | Roles Requeridos |
|----------|-----------------|
| GET /users | ADMIN, SYSTEM_ADMIN |
| GET /users/{id} | ADMIN, SYSTEM_ADMIN |
| GET /users/username/{username} | ADMIN, SYSTEM_ADMIN |
| POST /users | ADMIN, SYSTEM_ADMIN |
| PUT /users/{id} | ADMIN, SYSTEM_ADMIN |
| PATCH /users/{id}/disable | ADMIN, SYSTEM_ADMIN |
| PATCH /users/{id}/enable | ADMIN, SYSTEM_ADMIN |
| DELETE /users/{id} | **SYSTEM_ADMIN** |
| GET /users/tenant/{tenantCode} | ADMIN, SYSTEM_ADMIN |

---

## 🎨 Integración con Frontend

### Ejemplo React/TypeScript

```typescript
// types.ts
export interface User {
  id: number;
  username: string;
  email: string;
  fullName: string;
  role: string;
  enabled: boolean;
  createdAt: string;
  lastLoginAt?: string;
  tenant: {
    id: number;
    tenantCode: string;
    tenantName: string;
  };
}

export interface CreateUserRequest {
  username: string;
  password: string;
  email: string;
  fullName: string;
  tenantCode: string;
  role?: string;
  enabled?: boolean;
}

// api.ts
const API_URL = 'http://localhost:8080/api/v1';

export const userApi = {
  // Listar usuarios
  async getUsers(page = 0, size = 10) {
    const response = await fetch(
      `${API_URL}/users?page=${page}&size=${size}`,
      {
        headers: {
          'Authorization': `Bearer ${localStorage.getItem('token')}`
        }
      }
    );
    return response.json();
  },

  // Obtener usuario por ID
  async getUser(id: number): Promise<User> {
    const response = await fetch(`${API_URL}/users/${id}`, {
      headers: {
        'Authorization': `Bearer ${localStorage.getItem('token')}`
      }
    });
    return response.json();
  },

  // Crear usuario
  async createUser(data: CreateUserRequest): Promise<User> {
    const response = await fetch(`${API_URL}/users`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${localStorage.getItem('token')}`
      },
      body: JSON.stringify(data)
    });
    return response.json();
  },

  // Actualizar usuario
  async updateUser(id: number, data: Partial<User>): Promise<User> {
    const response = await fetch(`${API_URL}/users/${id}`, {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${localStorage.getItem('token')}`
      },
      body: JSON.stringify(data)
    });
    return response.json();
  },

  // Deshabilitar usuario
  async disableUser(id: number): Promise<User> {
    const response = await fetch(`${API_URL}/users/${id}/disable`, {
      method: 'PATCH',
      headers: {
        'Authorization': `Bearer ${localStorage.getItem('token')}`
      }
    });
    return response.json();
  },

  // Eliminar usuario
  async deleteUser(id: number): Promise<void> {
    await fetch(`${API_URL}/users/${id}`, {
      method: 'DELETE',
      headers: {
        'Authorization': `Bearer ${localStorage.getItem('token')}`
      }
    });
  }
};

// Ejemplo de uso en un componente
import { useState, useEffect } from 'react';

function UserList() {
  const [users, setUsers] = useState<User[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    async function fetchUsers() {
      try {
        const data = await userApi.getUsers();
        setUsers(data.users);
      } catch (error) {
        console.error('Error fetching users:', error);
      } finally {
        setLoading(false);
      }
    }
    
    fetchUsers();
  }, []);

  if (loading) return <div>Loading...</div>;

  return (
    <div>
      {users.map(user => (
        <div key={user.id}>
          <h3>{user.fullName}</h3>
          <p>{user.email}</p>
          <p>Role: {user.role}</p>
        </div>
      ))}
    </div>
  );
}
```

---

## 🧪 Testing

```bash
# 1. Login
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"superadmin","password":"superadmin123"}' \
  | jq -r '.token')

# 2. Listar usuarios
curl -X GET http://localhost:8080/api/v1/users \
  -H "Authorization: Bearer $TOKEN" | jq

# 3. Crear usuario
curl -X POST http://localhost:8080/api/v1/users \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "test-user",
    "password": "test123",
    "email": "test@acme.com",
    "fullName": "Test User",
    "tenantCode": "ACME",
    "role": "USER"
  }' | jq

# 4. Obtener usuario creado
USER_ID=$(curl -s -X GET http://localhost:8080/api/v1/users/username/test-user \
  -H "Authorization: Bearer $TOKEN" | jq -r '.id')

echo "Created user ID: $USER_ID"

# 5. Actualizar usuario
curl -X PUT http://localhost:8080/api/v1/users/$USER_ID \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"fullName": "Test User Updated"}' | jq

# 6. Deshabilitar usuario
curl -X PATCH http://localhost:8080/api/v1/users/$USER_ID/disable \
  -H "Authorization: Bearer $TOKEN" | jq

# 7. Eliminar usuario (requiere SYSTEM_ADMIN)
curl -X DELETE http://localhost:8080/api/v1/users/$USER_ID \
  -H "Authorization: Bearer $TOKEN"
```

---

## 🌐 Swagger UI

Una vez que la aplicación esté corriendo, puedes acceder a la documentación interactiva en:

```
http://localhost:8080/swagger-ui.html
```

Ahí puedes probar todos los endpoints desde el navegador.

---

## ⚠️ Validaciones

### CreateUserRequest
- `username`: Requerido, 3-50 caracteres
- `password`: Requerido, mínimo 6 caracteres
- `email`: Debe ser un email válido
- `fullName`: Requerido
- `tenantCode`: Requerido
- `role`: Opcional (default: "USER")
- `enabled`: Opcional (default: true)

### UpdateUserRequest
- Todos los campos son opcionales
- `password`: Si se proporciona, mínimo 6 caracteres

---

## 🔐 Seguridad

- Todos los endpoints requieren autenticación JWT
- Los passwords se almacenan encriptados con BCrypt
- Los passwords **nunca** se devuelven en las respuestas
- DELETE requiere rol SYSTEM_ADMIN
- Los demás endpoints requieren ADMIN o SYSTEM_ADMIN
