package com.atina.invoice.api.controller;

import com.atina.invoice.api.dto.request.CreateUserRequest;
import com.atina.invoice.api.dto.request.UpdateUserRequest;
import com.atina.invoice.api.model.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests de integración para UserController
 * Cubre todos los endpoints con diferentes roles y casos
 */
@DisplayName("UserController Integration Tests")
public class UserControllerTest extends BaseControllerTest {

    // ========================================
    // Tests: List Users (GET /api/v1/users)
    // ========================================

    @Nested
    @DisplayName("GET /api/v1/users - List Users")
    class ListUsersTests {

        @Test
        @DisplayName("SYSTEM_ADMIN puede listar usuarios")
        public void testListUsers_AsSystemAdmin_Success() throws Exception {
            mockMvc.perform(get("/api/v1/users")
                            .header("Authorization", "Bearer " + systemAdminToken)
                            .param("page", "0")
                            .param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.users").isArray())
                    .andExpect(jsonPath("$.users", hasSize(greaterThanOrEqualTo(4))))
                    .andExpect(jsonPath("$.currentPage").value(0))
                    .andExpect(jsonPath("$.totalItems").value(greaterThanOrEqualTo(4)));
        }

        @Test
        @DisplayName("ADMIN puede listar usuarios")
        public void testListUsers_AsAdmin_Success() throws Exception {
            mockMvc.perform(get("/api/v1/users")
                            .header("Authorization", "Bearer " + acmeAdminToken)
                            .param("page", "0")
                            .param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.users").isArray());
        }

        @Test
        @DisplayName("USER no puede listar usuarios")
        public void testListUsers_AsUser_Forbidden() throws Exception {
            mockMvc.perform(get("/api/v1/users")
                            .header("Authorization", "Bearer " + acmeUserToken))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Sin token devuelve Unauthorized")
        public void testListUsers_WithoutToken_Unauthorized() throws Exception {
            mockMvc.perform(get("/api/v1/users"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Paginación funciona correctamente")
        public void testListUsers_Pagination_Success() throws Exception {
            mockMvc.perform(get("/api/v1/users")
                            .header("Authorization", "Bearer " + systemAdminToken)
                            .param("page", "0")
                            .param("size", "2")
                            .param("sortBy", "username")
                            .param("sortDirection", "asc"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.users", hasSize(2)))
                    .andExpect(jsonPath("$.currentPage").value(0))
                    .andExpect(jsonPath("$.totalPages").value(greaterThanOrEqualTo(2)));
        }
    }

    // ========================================
    // Tests: Get User by ID (GET /api/v1/users/{id})
    // ========================================

    @Nested
    @DisplayName("GET /api/v1/users/{id} - Get User by ID")
    class GetUserByIdTests {

        @Test
        @DisplayName("ADMIN puede obtener usuario por ID")
        public void testGetUserById_AsAdmin_Success() throws Exception {
            mockMvc.perform(get("/api/v1/users/" + acmeUser.getId())
                            .header("Authorization", "Bearer " + acmeAdminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(acmeUser.getId()))
                    .andExpect(jsonPath("$.username").value("acme-user"))
                    .andExpect(jsonPath("$.email").value("acme-user@example.com"))
                    .andExpect(jsonPath("$.password").doesNotExist())
                    .andExpect(jsonPath("$.tenant.tenantCode").value("ACME"));
        }

        @Test
        @DisplayName("SYSTEM_ADMIN puede obtener cualquier usuario")
        public void testGetUserById_AsSystemAdmin_Success() throws Exception {
            mockMvc.perform(get("/api/v1/users/" + globexUser.getId())
                            .header("Authorization", "Bearer " + systemAdminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(globexUser.getId()))
                    .andExpect(jsonPath("$.tenant.tenantCode").value("GLOBEX"));
        }

        @Test
        @DisplayName("USER no puede obtener usuarios")
        public void testGetUserById_AsUser_Forbidden() throws Exception {
            mockMvc.perform(get("/api/v1/users/" + acmeAdmin.getId())
                            .header("Authorization", "Bearer " + acmeUserToken))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("ID inexistente devuelve NotFound")
        public void testGetUserById_NotFound() throws Exception {
            mockMvc.perform(get("/api/v1/users/99999")
                            .header("Authorization", "Bearer " + systemAdminToken))
                    .andExpect(status().isNotFound());
        }
    }

    // ========================================
    // Tests: Get User by Username (GET /api/v1/users/username/{username})
    // ========================================

    @Nested
    @DisplayName("GET /api/v1/users/username/{username} - Get User by Username")
    class GetUserByUsernameTests {

        @Test
        @DisplayName("ADMIN puede buscar usuario por username")
        public void testGetUserByUsername_AsAdmin_Success() throws Exception {
            mockMvc.perform(get("/api/v1/users/username/acme-user")
                            .header("Authorization", "Bearer " + acmeAdminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.username").value("acme-user"))
                    .andExpect(jsonPath("$.email").value("acme-user@example.com"));
        }

        @Test
        @DisplayName("Username inexistente devuelve NotFound")
        public void testGetUserByUsername_NotFound() throws Exception {
            mockMvc.perform(get("/api/v1/users/username/nonexistent-user")
                            .header("Authorization", "Bearer " + systemAdminToken))
                    .andExpect(status().isNotFound());
        }
    }

    // ========================================
    // Tests: Create User (POST /api/v1/users)
    // ========================================

    @Nested
    @DisplayName("POST /api/v1/users - Create User")
    class CreateUserTests {

        @Test
        @DisplayName("ADMIN puede crear usuario")
        public void testCreateUser_AsAdmin_Success() throws Exception {
            CreateUserRequest request = CreateUserRequest.builder()
                    .username("new-user")
                    .password("password123")
                    .email("new-user@acme.com")
                    .fullName("New User")
                    .tenantCode("ACME")
                    .role("USER")
                    .enabled(true)
                    .build();

            mockMvc.perform(post("/api/v1/users")
                            .header("Authorization", "Bearer " + acmeAdminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.username").value("new-user"))
                    .andExpect(jsonPath("$.email").value("new-user@acme.com"))
                    .andExpect(jsonPath("$.tenant.tenantCode").value("ACME"))
                    .andExpect(jsonPath("$.password").doesNotExist());
        }

        @Test
        @DisplayName("SYSTEM_ADMIN puede crear usuario")
        public void testCreateUser_AsSystemAdmin_Success() throws Exception {
            CreateUserRequest request = CreateUserRequest.builder()
                    .username("another-user")
                    .password("password123")
                    .email("another@system.com")
                    .fullName("Another User")
                    .tenantCode("SYSTEM")
                    .role("ADMIN")
                    .build();

            mockMvc.perform(post("/api/v1/users")
                            .header("Authorization", "Bearer " + systemAdminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.username").value("another-user"));
        }

        @Test
        @DisplayName("USER no puede crear usuarios")
        public void testCreateUser_AsUser_Forbidden() throws Exception {
            CreateUserRequest request = CreateUserRequest.builder()
                    .username("forbidden-user")
                    .password("password123")
                    .email("forbidden@acme.com")
                    .fullName("Forbidden User")
                    .tenantCode("ACME")
                    .build();

            mockMvc.perform(post("/api/v1/users")
                            .header("Authorization", "Bearer " + acmeUserToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(request)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Username duplicado devuelve BadRequest")
        public void testCreateUser_DuplicateUsername_BadRequest() throws Exception {
            CreateUserRequest request = CreateUserRequest.builder()
                    .username("acme-user")  // Usuario que ya existe
                    .password("password123")
                    .email("duplicate@acme.com")
                    .fullName("Duplicate User")
                    .tenantCode("ACME")
                    .build();

            mockMvc.perform(post("/api/v1/users")
                            .header("Authorization", "Bearer " + acmeAdminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Username already exists"));
        }

        @Test
        @DisplayName("Validación: username requerido")
        public void testCreateUser_MissingUsername_BadRequest() throws Exception {
            CreateUserRequest request = CreateUserRequest.builder()
                    .password("password123")
                    .email("test@acme.com")
                    .fullName("Test User")
                    .tenantCode("ACME")
                    .build();

            mockMvc.perform(post("/api/v1/users")
                            .header("Authorization", "Bearer " + acmeAdminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Validación: email inválido")
        public void testCreateUser_InvalidEmail_BadRequest() throws Exception {
            CreateUserRequest request = CreateUserRequest.builder()
                    .username("test-user")
                    .password("password123")
                    .email("invalid-email")  // Email inválido
                    .fullName("Test User")
                    .tenantCode("ACME")
                    .role("USER")
                    .enabled(true).role("USER")
                    .build();

            mockMvc.perform(post("/api/v1/users")
                            .header("Authorization", "Bearer " + acmeAdminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    // ========================================
    // Tests: Update User (PUT /api/v1/users/{id})
    // ========================================

    @Nested
    @DisplayName("PUT /api/v1/users/{id} - Update User")
    class UpdateUserTests {

        @Test
        @DisplayName("ADMIN puede actualizar usuario")
        public void testUpdateUser_AsAdmin_Success() throws Exception {
            UpdateUserRequest request = UpdateUserRequest.builder()
                    .email("updated@acme.com")
                    .fullName("Updated Name")
                    .role("ADMIN")
                    .build();

            mockMvc.perform(put("/api/v1/users/" + acmeUser.getId())
                            .header("Authorization", "Bearer " + acmeAdminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").value("updated@acme.com"))
                    .andExpect(jsonPath("$.fullName").value("Updated Name"))
                    .andExpect(jsonPath("$.role").value("ADMIN"));
        }

        @Test
        @DisplayName("Actualización parcial funciona")
        public void testUpdateUser_PartialUpdate_Success() throws Exception {
            UpdateUserRequest request = UpdateUserRequest.builder()
                    .email("newemail@acme.com")
                    .build();

            mockMvc.perform(put("/api/v1/users/" + acmeUser.getId())
                            .header("Authorization", "Bearer " + acmeAdminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").value("newemail@acme.com"))
                    .andExpect(jsonPath("$.fullName").value("ACME USER"));  // No cambió
        }

        @Test
        @DisplayName("USER no puede actualizar usuarios")
        public void testUpdateUser_AsUser_Forbidden() throws Exception {
            UpdateUserRequest request = UpdateUserRequest.builder()
                    .email("forbidden@acme.com")
                    .build();

            mockMvc.perform(put("/api/v1/users/" + acmeAdmin.getId())
                            .header("Authorization", "Bearer " + acmeUserToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(request)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("ID inexistente devuelve NotFound")
        public void testUpdateUser_NotFound() throws Exception {
            UpdateUserRequest request = UpdateUserRequest.builder()
                    .email("test@acme.com")
                    .build();

            mockMvc.perform(put("/api/v1/users/99999")
                            .header("Authorization", "Bearer " + systemAdminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(request)))
                    .andExpect(status().isNotFound());
        }
    }

    // ========================================
    // Tests: Disable User (PATCH /api/v1/users/{id}/disable)
    // ========================================

    @Nested
    @DisplayName("PATCH /api/v1/users/{id}/disable - Disable User")
    class DisableUserTests {

        @Test
        @DisplayName("ADMIN puede deshabilitar usuario")
        public void testDisableUser_AsAdmin_Success() throws Exception {
            mockMvc.perform(patch("/api/v1/users/" + acmeUser.getId() + "/disable")
                            .header("Authorization", "Bearer " + acmeAdminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.enabled").value(false));
        }

        @Test
        @DisplayName("USER no puede deshabilitar usuarios")
        public void testDisableUser_AsUser_Forbidden() throws Exception {
            mockMvc.perform(patch("/api/v1/users/" + acmeAdmin.getId() + "/disable")
                            .header("Authorization", "Bearer " + acmeUserToken))
                    .andExpect(status().isForbidden());
        }
    }

    // ========================================
    // Tests: Enable User (PATCH /api/v1/users/{id}/enable)
    // ========================================

    @Nested
    @DisplayName("PATCH /api/v1/users/{id}/enable - Enable User")
    class EnableUserTests {

        @Test
        @DisplayName("ADMIN puede habilitar usuario")
        public void testEnableUser_AsAdmin_Success() throws Exception {
            // Primero deshabilitar
            User disabledUser = acmeUser;
            disabledUser.setEnabled(false);
            userRepository.save(disabledUser);

            // Luego habilitar
            mockMvc.perform(patch("/api/v1/users/" + disabledUser.getId() + "/enable")
                            .header("Authorization", "Bearer " + acmeAdminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.enabled").value(true));
        }
    }

    // ========================================
    // Tests: Get Users by Tenant (GET /api/v1/users/tenant/{tenantCode})
    // ========================================

    @Nested
    @DisplayName("GET /api/v1/users/tenant/{tenantCode} - Get Users by Tenant")
    class GetUsersByTenantTests {

        @Test
        @DisplayName("ADMIN puede obtener usuarios por tenant")
        public void testGetUsersByTenant_AsAdmin_Success() throws Exception {
            mockMvc.perform(get("/api/v1/users/tenant/ACME")
                            .header("Authorization", "Bearer " + acmeAdminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$", hasSize(2)))  // acme-admin y acme-user
                    .andExpect(jsonPath("$[*].tenant.tenantCode", everyItem(is("ACME"))));
        }

        @Test
        @DisplayName("SYSTEM_ADMIN puede obtener usuarios de cualquier tenant")
        public void testGetUsersByTenant_AsSystemAdmin_Success() throws Exception {
            mockMvc.perform(get("/api/v1/users/tenant/GLOBEX")
                            .header("Authorization", "Bearer " + systemAdminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$", hasSize(1)));
        }

        @Test
        @DisplayName("Tenant inexistente devuelve NotFound")
        public void testGetUsersByTenant_NotFound() throws Exception {
            mockMvc.perform(get("/api/v1/users/tenant/NONEXISTENT")
                            .header("Authorization", "Bearer " + systemAdminToken))
                    .andExpect(status().isNotFound());
        }
    }

    // ========================================
    // Tests: Delete User (DELETE /api/v1/users/{id})
    // ========================================

    @Nested
    @DisplayName("DELETE /api/v1/users/{id} - Delete User")
    class DeleteUserTests {

        @Test
        @DisplayName("SYSTEM_ADMIN puede eliminar usuario")
        public void testDeleteUser_AsSystemAdmin_Success() throws Exception {
            mockMvc.perform(delete("/api/v1/users/" + acmeUser.getId())
                            .header("Authorization", "Bearer " + systemAdminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("User deleted successfully"));

            // Verificar que el usuario fue eliminado
            mockMvc.perform(get("/api/v1/users/" + acmeUser.getId())
                            .header("Authorization", "Bearer " + systemAdminToken))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("ADMIN no puede eliminar usuarios")
        public void testDeleteUser_AsAdmin_Forbidden() throws Exception {
            mockMvc.perform(delete("/api/v1/users/" + acmeUser.getId())
                            .header("Authorization", "Bearer " + acmeAdminToken))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("USER no puede eliminar usuarios")
        public void testDeleteUser_AsUser_Forbidden() throws Exception {
            mockMvc.perform(delete("/api/v1/users/" + acmeAdmin.getId())
                            .header("Authorization", "Bearer " + acmeUserToken))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("ID inexistente devuelve NotFound")
        public void testDeleteUser_NotFound() throws Exception {
            mockMvc.perform(delete("/api/v1/users/99999")
                            .header("Authorization", "Bearer " + systemAdminToken))
                    .andExpect(status().isNotFound());
        }
    }
}
