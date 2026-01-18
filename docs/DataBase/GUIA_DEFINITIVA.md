# 🚀 Guía Definitiva: Hibernate (Dev) + Flyway (Prod)

## 🎯 Estrategia

```
DESARROLLO:
✅ Hibernate crea/actualiza tablas automáticamente (ddl-auto: update)
✅ Sin Flyway, sin migraciones SQL
✅ Desarrollo rápido como con H2

PRODUCCIÓN:
✅ Flyway ejecuta migraciones SQL versionadas
✅ Hibernate solo valida (ddl-auto: none)
✅ Control total del schema
```

---

## 📦 PARTE 1: Configurar Desarrollo (5 min)

### 1. Limpiar el desastre actual

```bash
# Limpiar base de datos
docker exec -it ce-base-db-backend mysql -u root -pCEBase2026! -e "
DROP DATABASE IF EXISTS invoice_extractor_dev;
CREATE DATABASE invoice_extractor_dev CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
"

# Borrar migraciones (no las necesitas en dev)
rm -rf src/main/resources/db/migration

# Limpiar compilación
mvn clean
```

### 2. Copiar archivos de configuración

**Reemplaza estos archivos en `src/main/resources/`:**

- `application-dev.yml` (el que te di)
- `application-prod.yml` (el que te di)

### 3. Ejecutar en desarrollo

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

**Resultado:**
```
✅ Profile "dev" activo
✅ Flyway deshabilitado
✅ Hibernate crea las 4 tablas automáticamente
✅ MultiTenancyDataInitializer inserta datos
✅ Aplicación lista
```

---

## 🔧 PARTE 2: Trabajar en Desarrollo

### Agregar una columna nueva

**Ejemplo:** Agregar `phone` a la tabla `users`

```java
// 1. Modificar entidad User.java
@Entity
@Table(name = "users")
public class User {
    // ... campos existentes ...
    
    @Column
    private String phone;  // ⭐ Nueva columna
    
    // ... getters/setters ...
}
```

```bash
# 2. Reiniciar aplicación
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# ✅ Hibernate detecta el cambio y ejecuta:
# ALTER TABLE users ADD COLUMN phone VARCHAR(255)
```

**No necesitas hacer nada más.** Hibernate actualiza la tabla automáticamente.

---

## 📦 PARTE 3: Preparar para Producción

### Cuando vayas a producción, sigue estos pasos:

#### Paso 1: Generar SQL desde tu base de desarrollo

```bash
# Exportar schema actual de MySQL dev
docker exec -it ce-base-db-backend mysqldump \
  -u root \
  -pCEBase2026! \
  --no-data \
  --skip-add-drop-table \
  --skip-comments \
  invoice_extractor_dev \
  > schema_export.sql
```

#### Paso 2: Crear migración Flyway

```bash
# Crear carpeta de migraciones
mkdir -p src/main/resources/db/migration

# Crear V1 (primera versión)
cat > src/main/resources/db/migration/V1__initial_schema.sql << 'EOF'
-- Copiar contenido de schema_export.sql aquí
-- Limpiar un poco (quitar cosas innecesarias de mysqldump)
EOF
```

#### Paso 3: Desplegar a producción

```bash
# Configurar variables de entorno
export DB_HOST=prod-mysql-host
export DB_PORT=3306
export DB_USERNAME=prod_user
export DB_PASSWORD=secure_password

# Ejecutar con profile prod
mvn spring-boot:run -Dspring.profiles.active=prod
```

**¿Qué pasa?**
1. ✅ Flyway ejecuta V1__initial_schema.sql
2. ✅ Crea todas las tablas en producción
3. ✅ Hibernate valida (no modifica nada)
4. ✅ Aplicación arranca

---

## 🔄 PARTE 4: Cambios Futuros

### Agregar una columna en el futuro

**Desarrollo:**
```java
// 1. Modificar entidad
@Column
private String newField;

// 2. Reiniciar
mvn spring-boot:run -Dspring-boot.run.profiles=dev

// ✅ Hibernate agrega la columna automáticamente
```

**Producción:**

```bash
# 1. Generar SQL desde dev
docker exec -it ce-base-db-backend mysql -u root -pCEBase2026! -e "
SHOW CREATE TABLE invoice_extractor_dev.users;
" | grep newField

# Salida ejemplo:
# `newField` varchar(255) DEFAULT NULL

# 2. Crear V2
cat > src/main/resources/db/migration/V2__add_user_phone.sql << 'EOF'
ALTER TABLE users ADD COLUMN phone VARCHAR(255);
EOF

# 3. Desplegar a prod
mvn spring-boot:run -Dspring.profiles.active=prod

# ✅ Flyway ejecuta V2 automáticamente
```

---

## 📊 Resumen de Archivos

### Configuración

```
src/main/resources/
├── application.yml              ← Config base (ya lo tienes)
├── application-dev.yml          ← Hibernate solo (nuevo)
└── application-prod.yml         ← Flyway habilitado (nuevo)
```

### Migraciones (solo cuando vayas a prod)

```
src/main/resources/db/migration/
├── V1__initial_schema.sql       ← Crear cuando vayas a prod
├── V2__add_some_column.sql      ← Cambios futuros
└── V3__add_another_table.sql    ← Más cambios
```

---

## ✅ Checklist Implementación

### Ahora (Desarrollo):

```bash
☐ Limpiar BD: DROP DATABASE invoice_extractor_dev; CREATE...
☐ Borrar: rm -rf src/main/resources/db/migration
☐ Copiar: application-dev.yml
☐ Copiar: application-prod.yml
☐ Ejecutar: mvn clean
☐ Ejecutar: mvn spring-boot:run -Dspring-boot.run.profiles=dev
☐ Ver: "Started InvoiceExtractorApiApplication"
```

### Más Tarde (Producción):

```bash
☐ Exportar schema de dev
☐ Crear db/migration/V1__initial_schema.sql
☐ Configurar variables de entorno
☐ Ejecutar con profile prod
```

---

## 🎯 Ventajas de Este Enfoque

**Desarrollo (Hibernate):**
- ✅ Rápido: cambias entidad → reinicia → funciona
- ✅ Sin pensar en SQL manualmente
- ✅ Como trabajabas con H2

**Producción (Flyway):**
- ✅ Control total del schema
- ✅ Migraciones versionadas
- ✅ Rollback posible
- ✅ Auditoría de cambios

---

## ⚠️ Importante

1. **NUNCA** uses `ddl-auto: update` en producción
2. **NUNCA** habilites Flyway en desarrollo (complica tu vida)
3. **SIEMPRE** genera las migraciones desde tu base de desarrollo

---

## 🔍 Verificación

### En desarrollo debe decir:

```
INFO - The following 1 profile is active: "dev"
INFO - HHH10001501: Connection obtained from JdbcConnectionAccess
INFO - HHH10001501: Hibernate: create table tenants...  // Primera vez
INFO - Initializing multi-tenancy data...
INFO - Started InvoiceExtractorApiApplication
```

### En producción debe decir:

```
INFO - The following 1 profile is active: "prod"
INFO - Flyway: Migrating schema to version "1 - initial schema"
INFO - Started InvoiceExtractorApiApplication
```

---

**Tiempo total implementación:** 5 minutos  
**Complejidad en dev:** CERO (igual que H2)  
**Control en prod:** TOTAL (Flyway profesional)
