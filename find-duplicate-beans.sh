#!/bin/bash

# Script para encontrar posibles beans duplicados o ambiguos

echo "🔍 Buscando posibles problemas de beans..."
echo "=========================================="
echo ""

PROJECT_DIR="${1:-.}"
SRC_DIR="$PROJECT_DIR/src/main/java"

echo "📁 Directorio: $SRC_DIR"
echo ""

# Función para contar ocurrencias
count_beans() {
    local bean_type=$1
    local pattern=$2
    echo "🔎 Buscando: $bean_type"
    echo "--------------------"
    grep -r "$pattern" "$SRC_DIR" 2>/dev/null | grep -v ".class" | while read -r line; do
        echo "  $line"
    done
    local count=$(grep -r "$pattern" "$SRC_DIR" 2>/dev/null | grep -v ".class" | wc -l)
    echo "  Total encontrado: $count"
    if [ "$count" -gt 1 ]; then
        echo "  ⚠️  WARNING: Múltiples definiciones encontradas!"
    fi
    echo ""
}

# Buscar PasswordEncoder beans
count_beans "PasswordEncoder" "@Bean.*passwordEncoder"

# Buscar AuthenticationManager beans
count_beans "AuthenticationManager" "@Bean.*authenticationManager"

# Buscar UserDetailsService implementaciones
count_beans "UserDetailsService" "implements UserDetailsService"

# Buscar AuthenticationProvider beans
count_beans "AuthenticationProvider" "@Bean.*authenticationProvider"

# Buscar SecurityFilterChain beans
count_beans "SecurityFilterChain" "@Bean.*securityFilterChain"

echo "=========================================="
echo "🔍 Buscar beans en archivos de configuración:"
echo ""

# Listar todos los @Bean en archivos de configuración
echo "📂 Beans en SecurityConfig:"
if [ -f "$SRC_DIR/com/atina/invoice/api/config/SecurityConfig.java" ]; then
    grep -n "@Bean" "$SRC_DIR/com/atina/invoice/api/config/SecurityConfig.java" | while read -r line; do
        echo "  $line"
    done
else
    echo "  ❌ No encontrado"
fi
echo ""

echo "📂 Beans en PasswordEncoderConfig:"
if [ -f "$SRC_DIR/com/atina/invoice/api/config/PasswordEncoderConfig.java" ]; then
    grep -n "@Bean" "$SRC_DIR/com/atina/invoice/api/config/PasswordEncoderConfig.java" | while read -r line; do
        echo "  $line"
    done
else
    echo "  ❌ No encontrado"
fi
echo ""

echo "📂 Beans en DataInitializer:"
if [ -f "$SRC_DIR/com/atina/invoice/api/config/DataInitializer.java" ]; then
    grep -n "@Bean" "$SRC_DIR/com/atina/invoice/api/config/DataInitializer.java" | while read -r line; do
        echo "  $line"
    done
else
    echo "  ❌ No encontrado"
fi
echo ""

echo "=========================================="
echo "🔍 Buscar @Primary y @Qualifier:"
echo ""

# Buscar @Primary
echo "📌 @Primary anotaciones:"
grep -r "@Primary" "$SRC_DIR" 2>/dev/null | grep -v ".class" | while read -r line; do
    echo "  $line"
done
echo ""

# Buscar @Qualifier
echo "🏷️  @Qualifier anotaciones:"
grep -r "@Qualifier" "$SRC_DIR" 2>/dev/null | grep -v ".class" | while read -r line; do
    echo "  $line"
done
echo ""

echo "=========================================="
echo "✅ Análisis completo"
echo ""
echo "💡 Si ves múltiples definiciones del mismo bean sin @Primary,"
echo "   ese probablemente es el problema."
